package com.posfarmacia.ventas.usecases.usecase;

import com.posfarmacia.contracts.api.EvaluarPromocionesRespuesta;
import com.posfarmacia.contracts.api.EvaluarPromocionesSolicitud;
import com.posfarmacia.contracts.api.ProductoDto;
import com.posfarmacia.contracts.api.ReservaRespuesta;
import com.posfarmacia.contracts.api.ReservaSolicitud;
import com.posfarmacia.ventas.domain.LineaVenta;
import com.posfarmacia.ventas.domain.Venta;
import com.posfarmacia.ventas.domain.VentaInvalidaException;
import com.posfarmacia.ventas.usecases.port.out.ServiciosExternosPort;
import com.posfarmacia.ventas.usecases.port.out.VentaPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que pasa mientras el cajero arma la venta: abrir, escanear productos, identificar
 * al cliente.
 *
 * <p>El metodo agregarLinea es el camino MAS caliente del sistema: se ejecuta una vez
 * por cada producto que el cajero escanea, lo que a 200 ventas/s de cinco lineas son
 * 1000 llamadas por segundo. Su presupuesto es p99 por debajo de 200 ms y hace
 * exactamente tres cosas:
 *
 * <ol>
 *   <li>Precio desde catalogo (cacheado, casi siempre memoria del pod).</li>
 *   <li>Reserva de stock en inventario. SINCRONA y critica: el cajero necesita saber
 *       ya si hay, no se le puede decir que espere con el cliente enfrente.</li>
 *   <li>Promociones. Degradable: si no contesta en 300 ms, la linea entra sin
 *       descuento y la venta continua.</li>
 * </ol>
 */
@Service
public class ArmarVentaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ArmarVentaUseCase.class);

    /** IGV peruano. Vive aca y no en el catalogo porque es una regla tributaria, no del producto. */
    private static final BigDecimal IGV = new BigDecimal("18.00");

    private final VentaPort ventas;
    private final ServiciosExternosPort servicios;
    private final DatosDenormalizados nombres;
    private final Clock reloj;

    public ArmarVentaUseCase(VentaPort ventas, ServiciosExternosPort servicios,
            DatosDenormalizados nombres, Clock reloj) {
        this.ventas = ventas;
        this.servicios = servicios;
        this.nombres = nombres;
        this.reloj = reloj;
    }

    /** Abrir una venta. Cero llamadas a otros servicios: es solo un INSERT local. */
    @Transactional
    public Venta iniciar(UUID localId, UUID cajaId, UUID sesionCajaId, UUID usuarioId) {
        Venta venta = new Venta(UUID.randomUUID(), Instant.now(reloj),
                localId, cajaId, sesionCajaId, usuarioId);
        ventas.guardar(venta);
        return venta;
    }

    @Transactional
    public Venta agregarLinea(UUID ventaId, UUID productoId, int cantidad, UUID recetaId) {
        Venta venta = cargar(ventaId);

        // 1. Precio. En lote aunque sea un solo producto: el mismo metodo sirve para
        //    cuando el POS manda varios de golpe, y evita dos caminos distintos.
        ProductoDto producto = servicios.productos(List.of(productoId)).stream()
                .findFirst()
                .orElseThrow(() -> new VentaInvalidaException("No existe el producto " + productoId));

        // El producto ya vino con su categoria: se aprende aqui para que el evento
        // de confirmacion la lleve sin una segunda consulta a catalogo.
        nombres.recordarCategoria(producto.id(), producto.categoriaId(), producto.categoriaNombre());

        if (producto.requiereReceta() && recetaId == null) {
            throw new VentaInvalidaException(
                    "El producto " + producto.nombreComercial() + " requiere receta");
        }

        // 2. Reserva de stock. Critica: sin esto no hay linea.
        ReservaRespuesta reserva = servicios.reservarStock(
                new ReservaSolicitud(ventaId, productoId, venta.localId(), cantidad));

        if (!reserva.reservada()) {
            throw new VentaInvalidaException(
                    "Sin stock de " + producto.nombreComercial() + ". " + reserva.motivo());
        }

        var linea = new LineaVenta(UUID.randomUUID(), ventaId, productoId,
                producto.nombreComercial(), cantidad, producto.precioVenta(), IGV,
                BigDecimal.ZERO, null, recetaId);
        venta.agregarLinea(linea);

        // 3. Promociones sobre la venta completa, no linea por linea. Degradable.
        Venta conDescuentos = aplicarPromociones(venta);
        ventas.guardar(conDescuentos);
        return conDescuentos;
    }

    /**
     * Evalua promociones para toda la venta de una sola llamada.
     *
     * <p>Pedirlas linea por linea seria una llamada por producto escaneado. Con 200
     * ventas/s de cinco lineas eso son 1000 req/s contra promociones solo por no haber
     * mandado las cinco juntas. El fan-out es lo que mata bajo carga, no la latencia
     * individual de cada llamada.
     */
    private Venta aplicarPromociones(Venta venta) {
        var solicitud = new EvaluarPromocionesSolicitud(
                venta.id(), venta.clienteId(),
                venta.lineas().stream()
                        .map(l -> new EvaluarPromocionesSolicitud.Linea(
                                l.productoId(), l.cantidad(), l.precioUnitario()))
                        .toList());

        EvaluarPromocionesRespuesta respuesta = servicios.evaluarPromociones(solicitud);

        if (respuesta.aplicables().isEmpty()) {
            return venta;
        }

        var reconstruida = new Venta(venta.id(), venta.fecha(), venta.localId(), venta.cajaId(),
                venta.sesionCajaId(), venta.usuarioId(), venta.clienteId(),
                venta.convenioSeguroId(), venta.lineaCreditoId(), venta.estado(), List.of());

        for (LineaVenta l : venta.lineas()) {
            var promo = respuesta.aplicables().stream()
                    .filter(p -> p.productoId().equals(l.productoId()))
                    .findFirst();

            reconstruida.agregarLinea(promo
                    .map(p -> new LineaVenta(l.id(), l.ventaId(), l.productoId(),
                            l.nombreProducto(), l.cantidad(), l.precioUnitario(),
                            l.tasaImpuesto(), p.descuento(), p.promocionId(), l.recetaId()))
                    .orElse(l));
        }
        return reconstruida;
    }

    @Transactional
    public Venta identificarCliente(UUID ventaId, String dni) {
        Venta venta = cargar(ventaId);
        var cliente = servicios.clientePorDni(dni);

        if (cliente == null) {
            // Clientes no respondio. La venta sigue como anonima: negarse a cobrar
            // porque no se pudo identificar al cliente le hace perder la venta a la botica.
            log.info("Venta {} continua como anonima: no se pudo identificar el DNI {}", ventaId, dni);
            return venta;
        }

        UUID convenio = cliente.convenios().isEmpty() ? null : cliente.convenios().get(0).convenioId();
        nombres.recordarCliente(cliente.id(), cliente.nombreCompleto());
        venta.identificarCliente(cliente.id(), convenio);
        ventas.guardar(venta);
        return venta;
    }

    @Transactional
    public Venta quitarLinea(UUID ventaId, UUID detalleId) {
        Venta venta = cargar(ventaId);
        venta.quitarLinea(detalleId);
        ventas.guardar(venta);
        return venta;
    }

    /**
     * Registra un pago de la venta.
     *
     * <p>No valida que la suma de pagos cubra el total: eso lo valida confirmar. Un
     * cajero puede registrar efectivo, despues tarjeta y despues el copago del seguro,
     * y entre uno y otro la venta esta legitimamente a medio pagar.
     */
    @Transactional
    public Venta registrarPago(UUID ventaId, UUID formaPagoId, BigDecimal monto,
            String codigoAutorizacion) {
        Venta venta = cargar(ventaId);
        ventas.registrarPago(ventaId, formaPagoId, monto, codigoAutorizacion);
        return venta;
    }

    /** Cuanto cubre el seguro y cuanto le queda por pagar al cliente. */
    public record Copago(BigDecimal montoCubierto, BigDecimal copago) {
    }

    /**
     * Aplica el convenio de seguro a la venta.
     *
     * <p>Consulta las coberturas en ms-clientes para los productos de la venta. Si el
     * servicio no responde, se devuelve cobertura cero en vez de fallar: el cajero
     * cobra el total y el cliente reclama despues, que es mejor que no poder cobrar.
     */
    @Transactional
    public Copago aplicarConvenio(UUID ventaId, UUID convenioId) {
        Venta venta = cargar(ventaId);
        var productos = venta.lineas().stream().map(LineaVenta::productoId).distinct().toList();
        var coberturas = servicios.coberturas(convenioId, productos);

        BigDecimal cubierto = BigDecimal.ZERO;
        for (LineaVenta l : venta.lineas()) {
            var cobertura = coberturas.stream()
                    .filter(c -> c.productoId().equals(l.productoId()))
                    .findFirst();
            if (cobertura.isPresent()) {
                cubierto = cubierto.add(l.total()
                        .multiply(cobertura.get().porcentajeCubierto())
                        .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
            }
        }

        venta.identificarCliente(venta.clienteId(), convenioId);
        ventas.guardar(venta);
        return new Copago(cubierto, venta.total().subtract(cubierto).max(BigDecimal.ZERO));
    }

    /** Suma de los pagos ya registrados: la pantalla muestra cuanto falta por cobrar. */
    public BigDecimal totalPagado(UUID ventaId) {
        return ventas.totalPagado(ventaId);
    }

    /** Lectura de la venta tal como esta. Sin llamadas a otros servicios. */
    public Venta obtener(UUID ventaId) {
        return cargar(ventaId);
    }

    private Venta cargar(UUID ventaId) {
        return ventas.porId(ventaId)
                .orElseThrow(() -> new VentaInvalidaException("No existe la venta " + ventaId));
    }
}

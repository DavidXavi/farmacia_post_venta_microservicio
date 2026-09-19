package com.posfarmacia.ventas.usecases.usecase;

import com.posfarmacia.contracts.api.EvaluarPromocionesSolicitud;
import com.posfarmacia.ventas.domain.LineaVenta;
import com.posfarmacia.ventas.domain.Venta;
import com.posfarmacia.ventas.domain.VentaInvalidaException;
import com.posfarmacia.ventas.usecases.port.out.ServiciosExternosPort;
import com.posfarmacia.ventas.usecases.port.out.VentaPort;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promociones sobre una venta en curso.
 *
 * <p>Va aparte de {@link ArmarVentaUseCase} porque es otra responsabilidad y otro modo
 * de fallar: armar la venta no puede continuar sin precio ni sin stock, y esto si
 * puede. Si ms-promociones no contesta, la respuesta es "no hay promociones" y el
 * cajero cobra la lista, que es exactamente lo que pasaba antes de que existieran las
 * promociones.
 *
 * <p>La evaluacion se pide para la venta completa aunque el cajero pregunte por una
 * linea: ms-promociones necesita ver el carrito entero para resolver un 2x1, y pedir
 * linea por linea multiplicaria las llamadas por el numero de productos.
 */
@Service
public class AplicarPromocionesUseCase {

    private static final Logger log = LoggerFactory.getLogger(AplicarPromocionesUseCase.class);

    private final VentaPort ventas;
    private final ServiciosExternosPort servicios;

    public AplicarPromocionesUseCase(VentaPort ventas, ServiciosExternosPort servicios) {
        this.ventas = ventas;
        this.servicios = servicios;
    }

    /** Lo que la pantalla muestra: id, nombre y cuanto ahorra. */
    public record Disponible(UUID id, UUID productoId, String nombre, String tipoBeneficio,
                             java.math.BigDecimal descuento) {
    }

    /**
     * Promociones que aplican a una linea concreta.
     *
     * <p>Si {@code detalleId} es null, devuelve las de toda la venta.
     */
    public List<Disponible> disponibles(UUID ventaId, UUID detalleId) {
        Venta venta = cargar(ventaId);
        if (venta.lineas().isEmpty()) {
            return List.of();
        }

        UUID productoFiltrado = detalleId == null ? null : lineaDe(venta, detalleId).productoId();

        return servicios.evaluarPromociones(solicitudDe(venta)).aplicables().stream()
                .filter(p -> productoFiltrado == null || productoFiltrado.equals(p.productoId()))
                .map(p -> new Disponible(p.promocionId(), p.productoId(), p.nombre(),
                        p.tipoBeneficio(), p.descuento()))
                .toList();
    }

    /**
     * Aplica una promocion a una linea.
     *
     * <p>El descuento se vuelve a pedir a ms-promociones en vez de aceptar el que manda
     * la pantalla. Confiar en el monto del cliente seria dejar que cualquiera con la
     * consola del navegador abierta se regale el descuento que quiera.
     */
    @Transactional
    public Venta aplicar(UUID ventaId, UUID detalleId, UUID promocionId) {
        Venta venta = cargar(ventaId);
        LineaVenta linea = lineaDe(venta, detalleId);

        var elegida = servicios.evaluarPromociones(solicitudDe(venta)).aplicables().stream()
                .filter(p -> promocionId.equals(p.promocionId()))
                .filter(p -> linea.productoId().equals(p.productoId()))
                .findFirst()
                .orElseThrow(() -> new VentaInvalidaException(
                        "La promocion no aplica a esta linea o ya no esta vigente"));

        venta.aplicarPromocion(detalleId, elegida.promocionId(), elegida.descuento());
        ventas.guardar(venta);
        log.info("Venta {}: promocion {} aplicada a la linea {} por {}",
                ventaId, elegida.nombre(), detalleId, elegida.descuento());
        return venta;
    }

    private EvaluarPromocionesSolicitud solicitudDe(Venta venta) {
        return new EvaluarPromocionesSolicitud(venta.id(), venta.clienteId(),
                venta.lineas().stream()
                        .map(l -> new EvaluarPromocionesSolicitud.Linea(
                                l.productoId(), l.cantidad(), l.precioUnitario()))
                        .toList());
    }

    private LineaVenta lineaDe(Venta venta, UUID detalleId) {
        return venta.lineas().stream()
                .filter(l -> l.id().equals(detalleId))
                .findFirst()
                .orElseThrow(() -> new VentaInvalidaException(
                        "La venta no tiene la linea " + detalleId));
    }

    private Venta cargar(UUID ventaId) {
        return ventas.porId(ventaId)
                .orElseThrow(() -> new VentaInvalidaException("No existe la venta " + ventaId));
    }
}

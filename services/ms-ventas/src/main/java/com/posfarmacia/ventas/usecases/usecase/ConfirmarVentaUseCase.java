package com.posfarmacia.ventas.usecases.usecase;

import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.VentaConfirmada;
import com.posfarmacia.ventas.domain.EstadoSaga;
import com.posfarmacia.ventas.domain.LineaVenta;
import com.posfarmacia.ventas.domain.Venta;
import com.posfarmacia.ventas.domain.VentaInvalidaException;
import com.posfarmacia.ventas.usecases.port.out.EventoPort;
import com.posfarmacia.ventas.usecases.port.out.SagaPort;
import com.posfarmacia.ventas.usecases.port.out.VentaPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmar la venta. El momento mas importante del sistema.
 *
 * <p><b>Lo que hace, y es todo lo que hace:</b> una transaccion local que cambia el
 * estado de la venta, abre la saga y escribe un evento en el outbox. Tres INSERT/UPDATE
 * contra la propia base y se responde al cajero. Presupuesto: p99 por debajo de 500 ms.
 *
 * <p><b>Lo que NO hace:</b> no llama a inventario, ni a credito, ni a facturacion, ni a
 * reportes. Esos cuatro se enteran despues, por el evento. Si esta operacion esperara a
 * los cuatro, el p99 seria la suma de los cuatro y la caida de cualquiera de ellos
 * pararia todas las cajas de la cadena.
 *
 * <p><b>Por que el outbox y no publicar a Kafka aqui mismo:</b> escribir en Postgres y
 * publicar en Kafka son dos sistemas. Si lo segundo falla despues de lo primero, la
 * venta quedo confirmada y el stock nunca se descuenta. Nadie se entera hasta el
 * inventario fisico, semanas despues. Con el outbox, la fila del evento y la fila de la
 * venta entran o no entran juntas.
 */
@Service
public class ConfirmarVentaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmarVentaUseCase.class);

    private final VentaPort ventas;
    private final SagaPort sagas;
    private final EventoPort eventos;
    private final DatosDenormalizados denormalizar;
    private final Counter iniciadas;

    public ConfirmarVentaUseCase(VentaPort ventas, SagaPort sagas, EventoPort eventos,
            DatosDenormalizados denormalizar, MeterRegistry metricas) {
        this.ventas = ventas;
        this.sagas = sagas;
        this.eventos = eventos;
        this.denormalizar = denormalizar;
        this.iniciadas = Counter.builder("saga.venta.iniciada.total")
                .description("Ventas confirmadas que abren una saga")
                .register(metricas);
    }

    /**
     * @param ventaId        la venta a confirmar
     * @param tipoComprobante BOLETA o FACTURA, lo decide el cajero. Nulo es BOLETA.
     */
    @Transactional
    public Venta confirmar(UUID ventaId, String tipoComprobante) {
        tipoComprobante = normalizarTipo(tipoComprobante);
        Venta venta = ventas.porId(ventaId)
                .orElseThrow(() -> new VentaInvalidaException("No existe la venta " + ventaId));

        venta.confirmar();
        ventas.guardar(venta);
        sagas.abrir(venta.id());

        // El evento lleva adentro nombres que ms-ventas no es dueno de (local, vendedor,
        // cliente, categoria del producto). Es duplicacion deliberada: sin ella,
        // ms-reportes tendria que llamar a tres servicios por cada venta que procesa y
        // dejaria de ser el read model rapido que justifica su existencia.
        VentaConfirmada evento = new VentaConfirmada(
                venta.id(), venta.fecha(), venta.localId(),
                denormalizar.nombreLocal(venta.localId()),
                venta.cajaId(), venta.usuarioId(),
                denormalizar.nombreUsuario(venta.usuarioId()),
                venta.clienteId(),
                denormalizar.nombreCliente(venta.clienteId()),
                venta.convenioSeguroId(), venta.lineaCreditoId(),
                venta.subtotal(), venta.descuento(), venta.impuesto(), venta.total(),
                tipoComprobante,
                lineasDe(venta));

        eventos.publicar("venta", venta.id(), Topicos.VENTAS_CONFIRMADAS, venta.localId(), evento);
        iniciadas.increment();

        log.info("Venta {} confirmada: {} lineas, total {}. Saga abierta.",
                venta.id(), venta.lineas().size(), venta.total());
        return venta;
    }

    /**
     * Facturacion decide la serie comparando contra "FACTURA" exacto. Un "Factura" que
     * pasara tal cual saldria como boleta B001 sin que nadie se entere, y ante SUNAT eso
     * es un comprobante mal emitido. Por eso se normaliza aqui, y lo desconocido se
     * rechaza en vez de caer en boleta.
     */
    static String normalizarTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return "BOLETA";
        }
        String t = tipo.trim().toUpperCase(java.util.Locale.ROOT);
        if (!t.equals("BOLETA") && !t.equals("FACTURA")) {
            throw new VentaInvalidaException(
                    "Tipo de comprobante desconocido: " + tipo + ". Los validos son BOLETA y FACTURA");
        }
        return t;
    }

    private List<VentaConfirmada.Linea> lineasDe(Venta venta) {
        return venta.lineas().stream()
                .map(this::aLineaEvento)
                .toList();
    }

    private VentaConfirmada.Linea aLineaEvento(LineaVenta l) {
        var categoria = denormalizar.categoriaDe(l.productoId());
        return new VentaConfirmada.Linea(
                l.id(), l.productoId(), l.nombreProducto(),
                categoria == null ? null : categoria.id(),
                categoria == null ? null : categoria.nombre(),
                l.cantidad(), l.precioUnitario(), l.descuento(), l.total(),
                l.promocionAplicadaId(), l.recetaId());
    }

    /**
     * Cierra la saga cuando los cuatro consumidores ya hicieron lo suyo.
     *
     * <p>Lo llaman los consumidores de LotesAsignados y ComprobanteEmitido. Cuando las
     * tres banderas estan en true, la saga se marca COMPLETADA y deja de aparecer en
     * el tablero de sagas abiertas.
     */
    @Transactional
    public void marcarPaso(UUID ventaId, SagaPort.Paso paso) {
        sagas.marcar(ventaId, paso);
        if (sagas.completa(ventaId)) {
            sagas.cerrar(ventaId, EstadoSaga.COMPLETADA);
            log.debug("Saga de la venta {} completa", ventaId);
        }
    }
}

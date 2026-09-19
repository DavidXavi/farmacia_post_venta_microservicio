package com.posfarmacia.facturacion.usecases.usecase;

import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.OperacionAuditada;
import com.posfarmacia.facturacion.usecases.port.out.ComprobantePort;
import com.posfarmacia.facturacion.usecases.port.out.DevolucionPort;
import com.posfarmacia.facturacion.usecases.port.out.VentaConsultadaPort;
import com.posfarmacia.plataforma.outbox.OutboxRegistrador;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registrar una devolucion y emitir su nota de credito.
 *
 * <p>Dos reglas sostienen todo lo demas, y las dos son de dinero:
 *
 * <ul>
 *   <li>El monto se calcula con el precio al que se vendio, consultado a ms-ventas,
 *       no con el que manda la pantalla. Si lo decidiera el cliente, devolver seria
 *       una forma de sacar plata de la caja.</li>
 *   <li>No se puede devolver mas de lo que se llevo, contando lo ya devuelto antes.
 *       Sin eso, tres devoluciones parciales de dos unidades sobre una venta de tres
 *       devuelven seis.</li>
 * </ul>
 *
 * <p>La devolucion y la nota de credito entran en la misma transaccion. Una devolucion
 * sin nota de credito es plata que salio de la caja sin documento que lo respalde, que
 * es exactamente lo que una fiscalizacion busca.
 */
@Service
public class RegistrarDevolucionUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarDevolucionUseCase.class);

    private final DevolucionPort devoluciones;
    private final ComprobantePort comprobantes;
    private final VentaConsultadaPort ventas;
    private final OutboxRegistrador outbox;
    private final Clock reloj;

    public RegistrarDevolucionUseCase(DevolucionPort devoluciones, ComprobantePort comprobantes,
            VentaConsultadaPort ventas, OutboxRegistrador outbox, Clock reloj) {
        this.devoluciones = devoluciones;
        this.comprobantes = comprobantes;
        this.ventas = ventas;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    public record LineaPedida(UUID detalleVentaId, int cantidad) {
    }

    public record Resultado(UUID devolucionId, UUID notaCreditoId, BigDecimal montoTotal) {
    }

    @Transactional
    public Resultado registrar(UUID ventaId, UUID usuarioId, String motivo,
            List<LineaPedida> pedidas) {
        if (pedidas == null || pedidas.isEmpty()) {
            throw new IllegalArgumentException("Indica al menos una linea a devolver");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException(
                    "La devolucion necesita un motivo: queda en la nota de credito");
        }

        var comprobante = comprobantes.porVentaId(ventaId)
                .orElseThrow(() -> new IllegalStateException(
                        "La venta no tiene comprobante emitido: no se puede emitir "
                                + "una nota de credito contra nada"));

        var venta = ventas.porId(ventaId);
        Map<UUID, Integer> yaDevuelto = devoluciones.yaDevueltoPorLinea(ventaId);

        var lineas = new ArrayList<DevolucionPort.LineaDevuelta>(pedidas.size());
        BigDecimal total = BigDecimal.ZERO;

        for (LineaPedida pedida : pedidas) {
            if (pedida.cantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad a devolver tiene que ser mayor que cero");
            }
            var vendida = venta.lineas().stream()
                    .filter(l -> l.detalleVentaId().equals(pedida.detalleVentaId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La venta no tiene la linea " + pedida.detalleVentaId()));

            int disponible = vendida.cantidad()
                    - yaDevuelto.getOrDefault(pedida.detalleVentaId(), 0);
            if (pedida.cantidad() > disponible) {
                throw new IllegalArgumentException(
                        "Se piden " + pedida.cantidad() + " unidades y solo quedan "
                                + disponible + " por devolver de esa linea");
            }

            BigDecimal monto = vendida.precioUnitarioConImpuesto()
                    .multiply(BigDecimal.valueOf(pedida.cantidad()));
            lineas.add(new DevolucionPort.LineaDevuelta(pedida.detalleVentaId(),
                    vendida.productoId(), pedida.cantidad(), monto));
            total = total.add(monto);
        }

        UUID devolucionId = UUID.randomUUID();
        UUID notaId = UUID.randomUUID();
        Instant ahora = Instant.now(reloj);

        devoluciones.insertarDevolucion(devolucionId, ventaId, comprobante.localId(),
                usuarioId, motivo, lineas);
        devoluciones.insertarNotaCredito(notaId, ventaId, comprobante.id(),
                comprobante.localId(), usuarioId,
                "Nota de credito por devolucion parcial: " + motivo, total, ahora);

        outbox.registrar("devolucion", devolucionId, Topicos.AUDITORIA, devolucionId,
                new OperacionAuditada(usuarioId, "ms-facturacion", "DEVOLUCION",
                        "venta", ventaId.toString(), motivo,
                        null, "S/ " + total, null, ahora));

        log.warn("Devolucion {} sobre la venta {}: S/ {} en {} linea(s) por {}",
                devolucionId, ventaId, total, lineas.size(), usuarioId);
        return new Resultado(devolucionId, notaId, total);
    }
}

package com.posfarmacia.facturacion.usecases.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Devoluciones y las notas de credito que las acompanan. */
public interface DevolucionPort {

    record LineaDevuelta(UUID detalleVentaId, UUID productoId, int cantidad,
                         BigDecimal montoDevuelto) {
    }

    void insertarDevolucion(UUID id, UUID ventaId, UUID localId, UUID usuarioId,
                            String motivo, List<LineaDevuelta> lineas);

    /**
     * Cuanto se devolvio ya de cada linea de esta venta.
     *
     * <p>Sin esto, dos devoluciones parciales del mismo producto podrian sumar mas
     * unidades de las que se vendieron y devolver dinero de mas.
     */
    java.util.Map<UUID, Integer> yaDevueltoPorLinea(UUID ventaId);

    void insertarNotaCredito(UUID id, UUID ventaId, UUID comprobanteId, UUID localId,
                             UUID usuarioId, String motivo, BigDecimal montoTotal,
                             Instant fecha);
}

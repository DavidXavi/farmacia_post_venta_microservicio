package com.posfarmacia.contracts.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Compensacion de la saga. Lo publica ms-ventas cuando una venta se anula, el pago
 * falla despues de haber reservado, o un consumidor reporta que no pudo completar
 * su parte.
 *
 * <p>Inventario devuelve los lotes, credito libera el monto reservado y facturacion
 * emite la nota de credito si el comprobante ya habia salido. Cada uno compensa lo
 * suyo: no hay transaccion distribuida que revierta las cuatro cosas de golpe.
 */
public record VentaAnulada(
        UUID ventaId,
        Instant fecha,
        UUID localId,
        UUID usuarioId,
        String motivo,
        boolean comprobanteYaEmitido,
        BigDecimal montoTotal,
        List<Linea> lineas) {

    public record Linea(UUID productoId, int cantidad) {
    }
}

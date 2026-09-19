package com.posfarmacia.contracts.eventos;

import java.time.Instant;
import java.util.UUID;

/** Movimiento de inventario, para el read model de reportes y para auditoria de stock. */
public record StockMovimiento(
        UUID movimientoId,
        UUID loteId,
        UUID productoId,
        UUID localId,
        String tipo,
        int cantidad,
        UUID usuarioId,
        String referencia,
        Instant fecha) {
}

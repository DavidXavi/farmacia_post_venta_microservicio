package com.posfarmacia.contracts.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Reservar stock. Es UNA de las dos unicas llamadas sincronas que pueden impedir una
 * venta (la otra es reservar credito), porque el cajero necesita saber ya si hay stock.
 *
 * <p>La reserva es idempotente por (ventaId, productoId): un reintento del POS no
 * aparta el doble.
 */
public record ReservaSolicitud(
        @NotNull UUID ventaId,
        @NotNull UUID productoId,
        @NotNull UUID localId,
        @Min(1) int cantidad) {
}

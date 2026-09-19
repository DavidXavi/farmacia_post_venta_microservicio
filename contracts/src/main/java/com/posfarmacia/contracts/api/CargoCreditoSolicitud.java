package com.posfarmacia.contracts.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Reservar credito para una venta. Idempotente por ventaId. */
public record CargoCreditoSolicitud(
        @NotNull UUID ventaId,
        @NotNull UUID clienteId,
        @NotNull @Positive BigDecimal monto) {
}

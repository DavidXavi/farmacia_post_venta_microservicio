package com.posfarmacia.contracts.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Cuanto cubre el seguro de este cliente para este producto (copago). */
public record CoberturaDto(
        UUID convenioId,
        UUID productoId,
        BigDecimal porcentajeCubierto) {
}

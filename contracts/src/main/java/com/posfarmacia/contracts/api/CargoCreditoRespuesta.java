package com.posfarmacia.contracts.api;

import java.math.BigDecimal;
import java.util.UUID;

public record CargoCreditoRespuesta(
        UUID reservaId,
        UUID lineaCreditoId,
        boolean aprobado,
        BigDecimal saldoRestante,
        String motivo) {
}

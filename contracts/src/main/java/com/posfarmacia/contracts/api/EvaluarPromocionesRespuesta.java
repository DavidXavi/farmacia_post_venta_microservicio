package com.posfarmacia.contracts.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EvaluarPromocionesRespuesta(List<PromocionAplicable> aplicables) {

    public record PromocionAplicable(
            UUID productoId,
            UUID promocionId,
            String nombre,
            String tipoBeneficio,
            BigDecimal descuento) {
    }

    /** Respuesta degradada: promociones no contesto a tiempo y la venta sigue sin ellas. */
    public static EvaluarPromocionesRespuesta vacia() {
        return new EvaluarPromocionesRespuesta(List.of());
    }
}

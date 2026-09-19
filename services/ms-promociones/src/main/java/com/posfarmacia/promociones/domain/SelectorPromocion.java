package com.posfarmacia.promociones.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Elige UNA promocion por linea: la que mas le conviene al cliente.
 *
 * <p>Una sola, no todas las que apliquen. Acumular descuentos es como una botica
 * termina vendiendo por debajo del costo sin que nadie lo note hasta el cierre de mes.
 * Es la regla RN07 del sistema original y se conserva tal cual.
 */
public final class SelectorPromocion {

    private SelectorPromocion() {
    }

    public record Elegida(UUID promocionId, String nombre, String tipoBeneficio, BigDecimal descuento) {
    }

    public static Optional<Elegida> mejorPara(List<ReglaPromocion> reglas, UUID productoId,
            int cantidad, BigDecimal precioUnitario, boolean hayCliente, LocalDate dia) {
        return reglas.stream()
                .filter(r -> r.vigente(dia))
                .filter(r -> r.aplicaA(productoId, cantidad, hayCliente))
                .map(r -> new Elegida(r.id(), r.nombre(), r.tipoBeneficio(),
                        r.descuentoPara(cantidad, precioUnitario)))
                .max(Comparator.comparing(Elegida::descuento)
                        // Desempate estable por id: dos promociones con el mismo
                        // descuento tienen que elegirse siempre igual, o el mismo
                        // carrito daria resultados distintos entre replicas.
                        .thenComparing(e -> e.promocionId().toString()));
    }
}

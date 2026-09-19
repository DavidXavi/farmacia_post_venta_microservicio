package com.posfarmacia.promociones.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Una promocion y sus condiciones.
 *
 * <p>Dominio puro y sin dependencias: evaluar una promocion es aritmetica y
 * comparaciones de fecha. Precisamente por eso este servicio escala con CPU y no con
 * base de datos, y por eso tiene sentido separarlo: su cuello de botella no se parece
 * al de nadie mas.
 */
public record ReglaPromocion(
        UUID id,
        String nombre,
        String tipoBeneficio,
        BigDecimal valorBeneficio,
        boolean requiereCliente,
        int cantidadMinima,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFin,
        boolean activa,
        List<UUID> productos) {

    public boolean vigente(LocalDate dia) {
        if (!activa) {
            return false;
        }
        boolean empezo = vigenciaInicio == null || !dia.isBefore(vigenciaInicio);
        boolean noTermino = vigenciaFin == null || !dia.isAfter(vigenciaFin);
        return empezo && noTermino;
    }

    public boolean aplicaA(UUID productoId, int cantidad, boolean hayCliente) {
        if (requiereCliente && !hayCliente) {
            return false;
        }
        return cantidad >= cantidadMinima && productos.contains(productoId);
    }

    /**
     * Descuento sobre la linea.
     *
     * <p>Los tres tipos son los que acepta la tabla {@code promociones}:
     *
     * <ul>
     *   <li><b>DESCUENTO_PORCENTAJE</b>: {@code valorBeneficio} es el porcentaje sobre
     *       el importe de la linea. 10 son 10%.</li>
     *   <li><b>DESCUENTO_MONTO</b>: soles que se descuentan de la linea, UNA vez, no
     *       por unidad. Se eligio por linea porque es la lectura mas conservadora de
     *       "S/ 5 de descuento" y la que menos dinero regala si la promocion se cargo
     *       mal.</li>
     *   <li><b>LLEVA_N_PAGA_M</b>: {@code cantidadMinima} es N (el grupo) y
     *       {@code valorBeneficio} cuantas unidades de ese grupo van gratis. Un 2x1 es
     *       N=2 con 1 gratis; "lleva 3 paga 2" es N=3 con 1 gratis. Se cuenta por grupos
     *       completos: con 5 unidades y N=2 van 2 grupos, o sea 2 gratis, y la quinta se
     *       paga.</li>
     * </ul>
     *
     * <p>Un tipo desconocido descuenta cero en vez de fallar: una promocion mal cargada
     * no puede impedir que se cobre. El importe es el tope, siempre: ninguna promocion
     * puede terminar pagandole al cliente.
     */
    public BigDecimal descuentoPara(int cantidad, BigDecimal precioUnitario) {
        BigDecimal importe = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        BigDecimal descuento = switch (tipoBeneficio) {
            case "DESCUENTO_PORCENTAJE" -> importe.multiply(valorBeneficio)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case "DESCUENTO_MONTO" -> valorBeneficio;
            case "LLEVA_N_PAGA_M" -> gratisPorGrupos(cantidad, precioUnitario);
            default -> BigDecimal.ZERO;
        };
        return descuento.max(BigDecimal.ZERO).min(importe).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal gratisPorGrupos(int cantidad, BigDecimal precioUnitario) {
        if (cantidadMinima <= 0) {
            return BigDecimal.ZERO;
        }
        int grupos = cantidad / cantidadMinima;
        return valorBeneficio
                .multiply(BigDecimal.valueOf(grupos))
                .multiply(precioUnitario);
    }
}

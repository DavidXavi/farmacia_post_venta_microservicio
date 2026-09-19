package com.posfarmacia.promociones.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El calculo del descuento, que es camino de dinero.
 *
 * <p>Existe por un fallo concreto: el switch comparaba contra "PORCENTAJE" y
 * "MONTO_FIJO" mientras la base guardaba "DESCUENTO_PORCENTAJE", "DESCUENTO_MONTO" y
 * "LLEVA_N_PAGA_M". Todos los casos caian en el default, el descuento salia cero y
 * ninguna promocion aplicaba jamas. No fallaba nada: simplemente no descontaba, y por
 * eso nadie lo noto hasta probar el boton en el navegador.
 */
class ReglaPromocionTest {

    private static final UUID PRODUCTO = UUID.randomUUID();
    private static final LocalDate HOY = LocalDate.of(2026, 9, 19);

    private ReglaPromocion regla(String tipo, String valor, int cantidadMinima) {
        return new ReglaPromocion(UUID.randomUUID(), "prueba", tipo, new BigDecimal(valor),
                false, cantidadMinima, null, null, true, List.of(PRODUCTO));
    }

    @Test
    @DisplayName("El porcentaje se aplica sobre el importe completo de la linea")
    void porcentaje() {
        var descuento = regla("DESCUENTO_PORCENTAJE", "10", 1)
                .descuentoPara(3, new BigDecimal("12.50"));

        assertThat(descuento).isEqualByComparingTo("3.75");   // 10% de 37.50
    }

    @Test
    @DisplayName("El monto fijo descuenta una vez, no por unidad")
    void montoFijo() {
        var descuento = regla("DESCUENTO_MONTO", "5.00", 1)
                .descuentoPara(4, new BigDecimal("15.00"));

        assertThat(descuento).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("Un 2x1 regala una unidad por cada dos")
    void dosPorUno() {
        var dosPorUno = regla("LLEVA_N_PAGA_M", "1", 2);

        assertThat(dosPorUno.descuentoPara(2, new BigDecimal("10.50"))).isEqualByComparingTo("10.50");
        // Con 3 unidades hay un solo grupo completo: la tercera se paga.
        assertThat(dosPorUno.descuentoPara(3, new BigDecimal("10.50"))).isEqualByComparingTo("10.50");
        assertThat(dosPorUno.descuentoPara(4, new BigDecimal("10.50"))).isEqualByComparingTo("21.00");
    }

    @Test
    @DisplayName("Lleva 3 paga 2 no descuenta hasta completar el grupo")
    void llevaTresPagaDos() {
        var promo = regla("LLEVA_N_PAGA_M", "1", 3);

        assertThat(promo.descuentoPara(2, new BigDecimal("14.50"))).isEqualByComparingTo("0.00");
        assertThat(promo.descuentoPara(3, new BigDecimal("14.50"))).isEqualByComparingTo("14.50");
        assertThat(promo.descuentoPara(6, new BigDecimal("14.50"))).isEqualByComparingTo("29.00");
    }

    @Test
    @DisplayName("El descuento nunca supera el importe de la linea")
    void nuncaPagaAlCliente() {
        var exagerada = regla("DESCUENTO_MONTO", "999.00", 1);

        assertThat(exagerada.descuentoPara(1, new BigDecimal("12.50"))).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("Un tipo que nadie conoce descuenta cero en vez de romper la venta")
    void tipoDesconocido() {
        assertThat(regla("REGALO_SORPRESA", "50", 1).descuentoPara(2, new BigDecimal("10.00")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Una promocion que exige cliente no aplica a una venta anonima")
    void exigeCliente() {
        var soloClientes = new ReglaPromocion(UUID.randomUUID(), "fidelidad",
                "DESCUENTO_PORCENTAJE", new BigDecimal("15"), true, 1, null, null, true,
                List.of(PRODUCTO));

        assertThat(soloClientes.aplicaA(PRODUCTO, 1, false)).isFalse();
        assertThat(soloClientes.aplicaA(PRODUCTO, 1, true)).isTrue();
    }

    @Test
    @DisplayName("Fuera de vigencia no aplica, aunque este activa")
    void vigencia() {
        var vencida = new ReglaPromocion(UUID.randomUUID(), "verano", "DESCUENTO_PORCENTAJE",
                new BigDecimal("20"), false, 1,
                LocalDate.of(2026, 3, 30), LocalDate.of(2026, 6, 28), true, List.of(PRODUCTO));

        assertThat(vencida.vigente(HOY)).isFalse();
        assertThat(vencida.vigente(LocalDate.of(2026, 5, 1))).isTrue();
    }
}

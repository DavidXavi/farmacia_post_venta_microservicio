package com.posfarmacia.inventario.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El FEFO es la unica logica no trivial del servicio: una ordenacion, un reparto y un
 * caso de borde con dinero y salud de por medio. Lleva test.
 */
class AsignadorFefoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 18);
    private static final UUID PRODUCTO = UUID.randomUUID();
    private static final UUID LOCAL = UUID.randomUUID();

    private Lote lote(String codigo, LocalDate vence, int disponible) {
        return new Lote(UUID.randomUUID(), codigo, PRODUCTO, LOCAL, vence, disponible, "DISPONIBLE");
    }

    @Test
    void despacha_primero_el_lote_que_vence_antes() {
        var lejano = lote("L-LEJANO", HOY.plusMonths(12), 100);
        var proximo = lote("L-PROXIMO", HOY.plusMonths(1), 5);

        var asignaciones = AsignadorFefo.asignar(List.of(lejano, proximo), 3, HOY);

        assertThat(asignaciones).hasSize(1);
        assertThat(asignaciones.get(0).loteCodigo()).isEqualTo("L-PROXIMO");
        assertThat(asignaciones.get(0).cantidad()).isEqualTo(3);
    }

    @Test
    void reparte_entre_varios_lotes_cuando_el_primero_no_alcanza() {
        var proximo = lote("L-PROXIMO", HOY.plusMonths(1), 4);
        var medio = lote("L-MEDIO", HOY.plusMonths(6), 10);

        var asignaciones = AsignadorFefo.asignar(List.of(medio, proximo), 6, HOY);

        assertThat(asignaciones).extracting(AsignacionLote::loteCodigo)
                .containsExactly("L-PROXIMO", "L-MEDIO");
        assertThat(asignaciones).extracting(AsignacionLote::cantidad)
                .containsExactly(4, 2);
    }

    @Test
    void nunca_despacha_un_lote_vencido() {
        var vencido = lote("L-VENCIDO", HOY.minusDays(1), 100);
        var vigente = lote("L-VIGENTE", HOY.plusMonths(3), 2);

        var asignaciones = AsignadorFefo.asignar(List.of(vencido, vigente), 2, HOY);

        assertThat(asignaciones).extracting(AsignacionLote::loteCodigo).containsExactly("L-VIGENTE");
    }

    @Test
    void falla_cuando_los_lotes_vigentes_no_cubren_la_cantidad() {
        // El contador de stock puede decir que hay 100 porque no sabe de vencimientos.
        // Aca es donde esa discrepancia se detecta antes de despachar un vencido.
        var vencido = lote("L-VENCIDO", HOY.minusDays(1), 100);
        var vigente = lote("L-VIGENTE", HOY.plusMonths(3), 2);

        assertThatThrownBy(() -> AsignadorFefo.asignar(List.of(vencido, vigente), 10, HOY))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("se pidieron 10");
    }

    @Test
    void el_orden_es_estable_entre_ejecuciones_con_el_mismo_vencimiento() {
        var a = lote("A", HOY.plusMonths(2), 5);
        var b = lote("B", HOY.plusMonths(2), 5);

        var primera = AsignadorFefo.asignar(List.of(a, b), 7, HOY);
        var segunda = AsignadorFefo.asignar(List.of(b, a), 7, HOY);

        // Dos entregas del mismo evento tienen que producir la misma asignacion, o la
        // idempotencia del consumidor no sirve de nada.
        assertThat(primera).isEqualTo(segunda);
    }
}

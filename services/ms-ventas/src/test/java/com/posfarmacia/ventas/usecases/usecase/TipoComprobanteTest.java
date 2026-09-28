package com.posfarmacia.ventas.usecases.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.posfarmacia.ventas.domain.VentaInvalidaException;
import org.junit.jupiter.api.Test;

class TipoComprobanteTest {

    @Test
    void facturaEnCualquierFormaSaleComoFactura() {
        assertThat(ConfirmarVentaUseCase.normalizarTipo("Factura")).isEqualTo("FACTURA");
        assertThat(ConfirmarVentaUseCase.normalizarTipo(" factura ")).isEqualTo("FACTURA");
    }

    @Test
    void sinTipoEsBoleta() {
        assertThat(ConfirmarVentaUseCase.normalizarTipo(null)).isEqualTo("BOLETA");
        assertThat(ConfirmarVentaUseCase.normalizarTipo("")).isEqualTo("BOLETA");
        assertThat(ConfirmarVentaUseCase.normalizarTipo("Boleta")).isEqualTo("BOLETA");
    }

    @Test
    void unTipoDesconocidoSeRechazaEnVezDeCaerEnBoleta() {
        assertThatThrownBy(() -> ConfirmarVentaUseCase.normalizarTipo("Ticket"))
                .isInstanceOf(VentaInvalidaException.class)
                .hasMessageContaining("BOLETA y FACTURA");
    }
}

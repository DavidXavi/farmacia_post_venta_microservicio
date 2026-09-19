package com.posfarmacia.contracts.api;

import java.util.List;
import java.util.UUID;

/** Cliente con sus convenios vigentes, en una sola llamada: el POS los pide juntos. */
public record ClienteDto(
        UUID id,
        String dni,
        String nombres,
        String apellidos,
        String estado,
        List<ConvenioVigente> convenios) {

    public record ConvenioVigente(UUID convenioId, String nombre) {
    }

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }
}

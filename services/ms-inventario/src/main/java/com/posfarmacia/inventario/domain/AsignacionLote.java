package com.posfarmacia.inventario.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Cuanto sale de cada lote para cubrir una linea de venta. */
public record AsignacionLote(
        UUID loteId,
        String loteCodigo,
        LocalDate fechaVencimiento,
        int cantidad) {
}

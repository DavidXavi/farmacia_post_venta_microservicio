package com.posfarmacia.inventario.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lote de un producto en un local.
 *
 * <p>En farmacia el lote no es un detalle de inventario, es trazabilidad regulatoria:
 * si manana hay alerta sanitaria sobre un lote, hay que poder decir a quien se le
 * vendio cada unidad.
 */
public record Lote(
        UUID id,
        String codigo,
        UUID productoId,
        UUID localId,
        LocalDate fechaVencimiento,
        int cantidadDisponible,
        String estado) {

    public boolean vencido(LocalDate hoy) {
        return !fechaVencimiento.isAfter(hoy);
    }

    /** Un lote vencido no se puede despachar aunque el contador de stock lo cuente. */
    public boolean despachable(LocalDate hoy) {
        return "DISPONIBLE".equals(estado) && cantidadDisponible > 0 && !vencido(hoy);
    }
}

package com.posfarmacia.contracts.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Auditoria transversal. Cualquiera de los nueve servicios publica, ms-identidad
 * consume y guarda. Lleva traceId para poder cruzar la fila de auditoria con la
 * traza distribuida completa de esa operacion.
 */
public record OperacionAuditada(
        UUID usuarioId,
        String servicio,
        String accion,
        String entidad,
        String entidadId,
        String detalle,
        String datosAnteriores,
        String datosNuevos,
        String traceId,
        Instant fecha) {
}

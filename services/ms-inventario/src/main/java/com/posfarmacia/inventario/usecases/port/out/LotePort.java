package com.posfarmacia.inventario.usecases.port.out;

import com.posfarmacia.inventario.domain.AsignacionLote;
import com.posfarmacia.inventario.domain.Lote;
import java.util.List;
import java.util.UUID;

public interface LotePort {

    /** Lotes despachables de un producto en un local, ya ordenados por vencimiento. */
    List<Lote> disponiblesParaFefo(UUID productoId, UUID localId);

    /** Descuenta de los lotes, registra el movimiento y guarda la asignacion. */
    void aplicarSalida(UUID ventaId, UUID productoId, UUID localId, UUID usuarioId,
            List<AsignacionLote> asignaciones);

    /**
     * Devuelve al lote lo que se habia sacado. Compensacion por anulacion o devolucion.
     *
     * <p>Lleva local y usuario porque la devolucion tambien es un movimiento de
     * inventario y tiene que quedar registrada con su responsable: un ajuste de stock
     * sin autor es exactamente lo que una auditoria observa.
     */
    void revertirSalida(UUID ventaId, UUID productoId, UUID localId, UUID usuarioId);
}

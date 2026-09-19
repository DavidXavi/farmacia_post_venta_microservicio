package com.posfarmacia.inventario.usecases.port.out;

import com.posfarmacia.inventario.domain.EstadoReserva;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservaPort {

    record ReservaGuardada(UUID id, UUID ventaId, UUID productoId, UUID localId,
                           int cantidad, EstadoReserva estado, Instant expiraEn) {
    }

    /**
     * Guarda la reserva. Si ya existe una para (ventaId, productoId), devuelve la que
     * habia: eso es lo que hace idempotente el reintento del POS.
     */
    ReservaGuardada guardarSiNoExiste(UUID ventaId, UUID productoId, UUID localId,
            int cantidad, Instant expiraEn);

    Optional<ReservaGuardada> porId(UUID reservaId);

    List<ReservaGuardada> activasDeVenta(UUID ventaId);

    void cambiarEstado(UUID reservaId, EstadoReserva nuevo);

    /** Reservas ACTIVAS cuyo TTL ya paso. Las libera el job de limpieza. */
    List<ReservaGuardada> vencidas(int limite);
}

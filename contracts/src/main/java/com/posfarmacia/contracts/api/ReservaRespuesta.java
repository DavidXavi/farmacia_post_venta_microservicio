package com.posfarmacia.contracts.api;

import java.time.Instant;
import java.util.UUID;

/**
 * @param reservada false cuando no alcanza el stock. No es una excepcion: que un
 *                  producto se agote es un caso de negocio normal, y el cajero
 *                  necesita el numero exacto para ofrecerle al cliente lo que queda.
 */
public record ReservaRespuesta(
        UUID reservaId,
        boolean reservada,
        int disponibleRestante,
        Instant expiraEn,
        String motivo) {
}

package com.posfarmacia.contracts.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Un producto cambio en ms-catalogo. Sirve para que quien cachee catalogo tumbe su
 * entrada sin esperar al TTL.
 *
 * <p>Con TTL corto (60 s en el pod) un evento perdido se corrige solo. Por eso la
 * invalidacion puede ser asincrona sin causar un problema real.
 */
public record CatalogoCambiado(
        UUID productoId,
        String motivo,
        Instant fecha) {
}

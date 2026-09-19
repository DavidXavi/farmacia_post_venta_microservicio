package com.posfarmacia.contracts.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Un producto cambio en ms-catalogo. Todos los servicios que cachean catalogo lo
 * consumen para tumbar la entrada en sus dos niveles de cache (Caffeine local y Redis).
 *
 * <p>Con TTL corto (60 s en el pod, 10 min en Redis) un evento perdido se corrige
 * solo. Por eso la invalidacion puede ser asincrona sin causar un problema real.
 */
public record CatalogoCambiado(
        UUID productoId,
        String motivo,
        Instant fecha) {
}

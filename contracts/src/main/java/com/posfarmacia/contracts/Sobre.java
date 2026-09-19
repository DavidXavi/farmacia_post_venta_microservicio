package com.posfarmacia.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Sobre comun de todo evento que viaja por Kafka.
 *
 * <p>Los cuatro campos de arriba no son burocracia:
 * <ul>
 *   <li>{@code eventoId} es lo que permite al consumidor descartar repetidos. Kafka
 *       entrega al-menos-una-vez, asi que sin esto un reintento cobra dos veces.</li>
 *   <li>{@code version} es lo que deja evolucionar el payload sin romper consumidores
 *       viejos. Desplegar un productor nuevo que rompe consumidores viejos es el modo
 *       de falla mas comun y mas caro de los sistemas por eventos.</li>
 *   <li>{@code traceId} viaja en el evento, no solo en el header HTTP. Sin el, una saga
 *       que falla en el cuarto consumidor es imposible de seguir.</li>
 *   <li>{@code clave} es el localId: define la particion y con eso el orden.</li>
 * </ul>
 *
 * @param <T> el payload concreto del evento
 */
public record Sobre<T>(
        UUID eventoId,
        String tipo,
        int version,
        Instant ocurridoEn,
        String origen,
        String traceId,
        String clave,
        T datos) {

    public static <T> Sobre<T> de(String tipo, String origen, UUID clave, String traceId, T datos) {
        return new Sobre<>(UUID.randomUUID(), tipo, 1, Instant.now(), origen, traceId,
                clave.toString(), datos);
    }
}

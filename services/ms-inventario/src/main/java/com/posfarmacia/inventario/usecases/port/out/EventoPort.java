package com.posfarmacia.inventario.usecases.port.out;

import java.util.UUID;

/**
 * Publica un evento. La implementacion escribe en la tabla outbox DENTRO de la
 * transaccion de negocio, no manda a Kafka directo: publicar fuera de la transaccion
 * es la doble escritura que corrompe el estado en silencio.
 */
public interface EventoPort {

    void publicar(String agregado, UUID agregadoId, String tipo, UUID clave, Object datos);
}

package com.posfarmacia.ventas.usecases.port.out;

import java.util.UUID;

/**
 * Publica un evento escribiendo en la tabla outbox, dentro de la transaccion de negocio.
 * Nunca manda a Kafka directo: eso seria la doble escritura que corrompe en silencio.
 */
public interface EventoPort {

    void publicar(String agregado, UUID agregadoId, String tipo, UUID clave, Object datos);
}

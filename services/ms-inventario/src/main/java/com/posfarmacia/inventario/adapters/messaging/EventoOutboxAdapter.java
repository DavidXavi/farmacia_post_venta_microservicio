package com.posfarmacia.inventario.adapters.messaging;

import com.posfarmacia.inventario.usecases.port.out.EventoPort;
import com.posfarmacia.plataforma.outbox.OutboxRegistrador;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementa el puerto de eventos escribiendo en la tabla outbox, nunca mandando a
 * Kafka directo desde el caso de uso.
 *
 * <p>Esa diferencia es toda la garantia de consistencia del sistema: el evento y el
 * cambio de negocio entran o no entran juntos.
 */
@Component
public class EventoOutboxAdapter implements EventoPort {

    private final OutboxRegistrador outbox;

    public EventoOutboxAdapter(OutboxRegistrador outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publicar(String agregado, UUID agregadoId, String tipo, UUID clave, Object datos) {
        outbox.registrar(agregado, agregadoId, tipo, clave, datos);
    }
}

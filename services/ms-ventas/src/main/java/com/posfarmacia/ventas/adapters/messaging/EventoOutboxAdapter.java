package com.posfarmacia.ventas.adapters.messaging;

import com.posfarmacia.plataforma.outbox.OutboxRegistrador;
import com.posfarmacia.ventas.usecases.port.out.EventoPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

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

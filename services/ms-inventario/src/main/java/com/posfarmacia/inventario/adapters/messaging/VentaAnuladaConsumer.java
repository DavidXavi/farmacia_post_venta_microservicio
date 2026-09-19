package com.posfarmacia.inventario.adapters.messaging;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.posfarmacia.contracts.Sobre;
import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.VentaAnulada;
import com.posfarmacia.inventario.usecases.usecase.LiberarStockUseCase;
import com.posfarmacia.plataforma.idempotencia.ConsumoIdempotente;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Consume pos.ventas.anuladas y devuelve el stock. Es la compensacion de la saga. */
@Component
public class VentaAnuladaConsumer {

    private static final String CONSUMIDOR = "ms-inventario.venta-anulada";

    private final ObjectMapper json;
    private final ConsumoIdempotente idempotencia;
    private final LiberarStockUseCase liberar;

    public VentaAnuladaConsumer(ObjectMapper json, ConsumoIdempotente idempotencia,
            LiberarStockUseCase liberar) {
        this.json = json;
        this.idempotencia = idempotencia;
        this.liberar = liberar;
    }

    @KafkaListener(topics = Topicos.VENTAS_ANULADAS, groupId = "ms-inventario")
    @Transactional
    public void recibir(String mensaje, Acknowledgment ack) throws Exception {
        Sobre<VentaAnulada> sobre = json.readValue(mensaje, new TypeReference<>() {
        });
        idempotencia.unaVez(sobre.eventoId(), CONSUMIDOR, () -> liberar.compensar(sobre.datos()));
        ack.acknowledge();
    }
}

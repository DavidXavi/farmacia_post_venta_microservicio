package com.posfarmacia.facturacion.adapters.messaging;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.posfarmacia.contracts.Sobre;
import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.VentaConfirmada;
import com.posfarmacia.facturacion.usecases.usecase.EmitirComprobanteUseCase;
import com.posfarmacia.plataforma.idempotencia.ConsumoIdempotente;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra el comprobante en cuanto la venta se confirma.
 *
 * <p>Solo lo registra. El envio a SUNAT lo hace un job aparte, porque mezclar las dos
 * cosas ataria el consumo de Kafka al ritmo de un tercero lento: una SUNAT caida
 * acumularia lag en la particion y bloquearia las ventas siguientes de ese mismo local.
 */
@Component
public class VentaConfirmadaConsumer {

    private final ObjectMapper json;
    private final ConsumoIdempotente idempotencia;
    private final EmitirComprobanteUseCase emitir;

    public VentaConfirmadaConsumer(ObjectMapper json, ConsumoIdempotente idempotencia,
            EmitirComprobanteUseCase emitir) {
        this.json = json;
        this.idempotencia = idempotencia;
        this.emitir = emitir;
    }

    @KafkaListener(topics = Topicos.VENTAS_CONFIRMADAS, groupId = "ms-facturacion")
    @Transactional
    public void recibir(String mensaje, Acknowledgment ack) throws Exception {
        Sobre<VentaConfirmada> sobre = json.readValue(mensaje, new TypeReference<>() {
        });
        idempotencia.unaVez(sobre.eventoId(), "ms-facturacion.venta-confirmada",
                () -> emitir.registrar(sobre.datos()));
        ack.acknowledge();
    }
}

package com.posfarmacia.ventas.adapters.messaging;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.posfarmacia.contracts.Sobre;
import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.ComprobanteEmitido;
import com.posfarmacia.plataforma.idempotencia.ConsumoIdempotente;
import com.posfarmacia.ventas.usecases.port.out.SagaPort;
import com.posfarmacia.ventas.usecases.usecase.ConfirmarVentaUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierra la saga cuando SUNAT acepto el comprobante.
 *
 * <p>Puede llegar en segundos o al dia siguiente, si SUNAT estuvo caida. Esa espera no
 * bloquea nada: la venta ya se cobro, el cliente ya se fue, y lo unico pendiente es un
 * tramite tributario. Ese desacople es exactamente la razon de que facturacion sea un
 * servicio aparte.
 */
@Component
public class ComprobanteEmitidoConsumer {

    private static final String CONSUMIDOR = "ms-ventas.comprobante-emitido";

    private final ObjectMapper json;
    private final ConsumoIdempotente idempotencia;
    private final ConfirmarVentaUseCase saga;

    public ComprobanteEmitidoConsumer(ObjectMapper json, ConsumoIdempotente idempotencia,
            ConfirmarVentaUseCase saga) {
        this.json = json;
        this.idempotencia = idempotencia;
        this.saga = saga;
    }

    @KafkaListener(topics = Topicos.COMPROBANTES_EMITIDOS, groupId = "ms-ventas")
    @Transactional
    public void recibir(String mensaje, Acknowledgment ack) throws Exception {
        Sobre<ComprobanteEmitido> sobre = json.readValue(mensaje, new TypeReference<>() {
        });
        idempotencia.unaVez(sobre.eventoId(), CONSUMIDOR,
                () -> saga.marcarPaso(sobre.datos().ventaId(), SagaPort.Paso.COMPROBANTE));
        ack.acknowledge();
    }
}

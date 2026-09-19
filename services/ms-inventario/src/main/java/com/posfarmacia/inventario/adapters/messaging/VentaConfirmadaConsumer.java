package com.posfarmacia.inventario.adapters.messaging;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.posfarmacia.contracts.Sobre;
import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.VentaConfirmada;
import com.posfarmacia.inventario.usecases.usecase.ConfirmarStockVentaUseCase;
import com.posfarmacia.plataforma.idempotencia.ConsumoIdempotente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consume pos.ventas.confirmadas y cierra la parte de inventario de la saga.
 *
 * <p>El commit del offset es manual y va DESPUES de que el efecto ya ocurrio. Con
 * auto-commit, un pod que muere entre el commit y el efecto pierde la venta: el stock
 * nunca se descuenta y nadie se entera hasta el inventario fisico.
 */
@Component
public class VentaConfirmadaConsumer {

    private static final Logger log = LoggerFactory.getLogger(VentaConfirmadaConsumer.class);
    private static final String CONSUMIDOR = "ms-inventario.venta-confirmada";

    private final ObjectMapper json;
    private final ConsumoIdempotente idempotencia;
    private final ConfirmarStockVentaUseCase confirmar;

    public VentaConfirmadaConsumer(ObjectMapper json, ConsumoIdempotente idempotencia,
            ConfirmarStockVentaUseCase confirmar) {
        this.json = json;
        this.idempotencia = idempotencia;
        this.confirmar = confirmar;
    }

    @KafkaListener(topics = Topicos.VENTAS_CONFIRMADAS, groupId = "ms-inventario")
    @Transactional
    public void recibir(String mensaje, Acknowledgment ack) throws Exception {
        Sobre<VentaConfirmada> sobre = json.readValue(mensaje, new TypeReference<>() {
        });

        // La marca de procesado y el efecto van en la misma transaccion: si el efecto
        // falla, la marca se revierte con el y el reintento de Kafka vuelve a traerlo.
        boolean ejecutado = idempotencia.unaVez(sobre.eventoId(), CONSUMIDOR,
                () -> confirmar.confirmar(sobre.datos()));

        if (!ejecutado) {
            log.debug("Venta {} ya procesada por inventario, se descarta el duplicado",
                    sobre.datos().ventaId());
        }
        ack.acknowledge();
    }
}

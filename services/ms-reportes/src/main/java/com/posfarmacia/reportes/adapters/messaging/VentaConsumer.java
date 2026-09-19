package com.posfarmacia.reportes.adapters.messaging;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.posfarmacia.contracts.Sobre;
import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.VentaAnulada;
import com.posfarmacia.contracts.eventos.VentaConfirmada;
import com.posfarmacia.plataforma.idempotencia.ConsumoIdempotente;
import com.posfarmacia.reportes.usecases.port.out.ReadModelPort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * La unica puerta de entrada de datos a ms-reportes.
 *
 * <p>No hay endpoint de escritura en todo el servicio. Eso no es una limitacion, es la
 * definicion de un read model: si alguien pudiera escribir aqui por HTTP, el read model
 * podria divergir de los eventos y dejaria de ser reconstruible.
 *
 * <p>Que sea reconstruible importa: si manana se agrega una columna al tablero, se borra
 * la tabla y se reproduce el topico desde el principio. Ese es el motivo de que el
 * evento VentaConfirmada lleve los nombres adentro en vez de solo los ids.
 */
@Component
public class VentaConsumer {

    private final ObjectMapper json;
    private final ConsumoIdempotente idempotencia;
    private final ReadModelPort readModel;

    public VentaConsumer(ObjectMapper json, ConsumoIdempotente idempotencia,
            ReadModelPort readModel) {
        this.json = json;
        this.idempotencia = idempotencia;
        this.readModel = readModel;
    }

    @KafkaListener(topics = Topicos.VENTAS_CONFIRMADAS, groupId = "ms-reportes")
    @Transactional
    public void confirmada(String mensaje, Acknowledgment ack) throws Exception {
        Sobre<VentaConfirmada> sobre = json.readValue(mensaje, new TypeReference<>() {
        });
        idempotencia.unaVez(sobre.eventoId(), "ms-reportes.venta-confirmada",
                () -> readModel.proyectar(sobre.datos()));
        ack.acknowledge();
    }

    @KafkaListener(topics = Topicos.VENTAS_ANULADAS, groupId = "ms-reportes")
    @Transactional
    public void anulada(String mensaje, Acknowledgment ack) throws Exception {
        Sobre<VentaAnulada> sobre = json.readValue(mensaje, new TypeReference<>() {
        });
        idempotencia.unaVez(sobre.eventoId(), "ms-reportes.venta-anulada",
                () -> readModel.anular(sobre.datos().ventaId()));
        ack.acknowledge();
    }
}

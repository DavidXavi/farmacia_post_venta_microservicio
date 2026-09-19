package com.posfarmacia.identidad.adapters.messaging;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.posfarmacia.contracts.Sobre;
import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.OperacionAuditada;
import com.posfarmacia.plataforma.idempotencia.ConsumoIdempotente;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auditoria centralizada: cualquiera de los nueve servicios publica, identidad guarda.
 *
 * <p>Centralizada a proposito. Auditoria repartida en nueve bases significa que
 * responder "quien toco este lote el martes" son nueve consultas y un merge a mano.
 * Como ademas es asincrona, publicar un evento de auditoria no le cuesta latencia a la
 * operacion que se esta auditando.
 */
@Component
public class AuditoriaConsumer {

    private static final String INSERTAR = """
            INSERT INTO auditoria_operaciones
                (id, fecha, usuario_id, servicio, accion, entidad, entidad_id, detalle,
                 datos_anteriores, datos_nuevos, trace_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final ObjectMapper json;
    private final ConsumoIdempotente idempotencia;
    private final JdbcClient jdbc;

    public AuditoriaConsumer(ObjectMapper json, ConsumoIdempotente idempotencia, JdbcClient jdbc) {
        this.json = json;
        this.idempotencia = idempotencia;
        this.jdbc = jdbc;
    }

    @KafkaListener(topics = Topicos.AUDITORIA, groupId = "ms-identidad")
    @Transactional
    public void recibir(String mensaje, Acknowledgment ack) throws Exception {
        Sobre<OperacionAuditada> sobre = json.readValue(mensaje, new TypeReference<>() {
        });

        idempotencia.unaVez(sobre.eventoId(), "ms-identidad.auditoria", () -> {
            OperacionAuditada a = sobre.datos();
            jdbc.sql(INSERTAR)
                    .param(UUID.randomUUID())
                    .param(java.sql.Timestamp.from(a.fecha()))
                    .param(a.usuarioId()).param(a.servicio()).param(a.accion())
                    .param(a.entidad()).param(a.entidadId()).param(a.detalle())
                    .param(a.datosAnteriores()).param(a.datosNuevos()).param(a.traceId())
                    .update();
        });
        ack.acknowledge();
    }
}

package com.posfarmacia.plataforma.outbox;

import tools.jackson.databind.ObjectMapper;
import com.posfarmacia.contracts.Sobre;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra un evento en la tabla outbox.
 *
 * <p>La unica regla que importa de esta clase: se llama DENTRO de la transaccion que
 * hace el cambio de negocio. Escribir en Postgres y publicar en Kafka son dos sistemas
 * distintos; si el segundo falla despues del primero, la venta quedo confirmada y el
 * stock nunca se descontó. Eso es corrupcion silenciosa, del tipo que se descubre
 * semanas despues cuando el inventario fisico no cuadra.
 *
 * <p>Con el outbox, la fila del evento y la fila de la venta entran o no entran juntas.
 * Atomicidad real sin transacciones distribuidas.
 *
 * <p>Usa JdbcClient y no JPA a proposito: es un INSERT sin mapeo ni ciclo de vida.
 * Una entidad JPA para esto seria una clase de treinta lineas para no ganar nada.
 */
@Component
public class OutboxRegistrador {

    private static final String SQL = """
            INSERT INTO outbox (id, agregado, agregado_id, tipo, clave, payload, creado_en)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, now())
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final ObjectProvider<Tracer> tracer;
    private final String nombreServicio;

    /**
     * El {@code Tracer} entra como {@link ObjectProvider} a propósito: la traza es
     * observabilidad, no negocio. Si el stack de tracing no está configurado, el evento
     * se registra igual con el traceId vacío. Exigir el bean convertiría una pieza
     * opcional en un requisito para poder vender.
     */
    public OutboxRegistrador(JdbcClient jdbc, ObjectMapper json, ObjectProvider<Tracer> tracer,
            @org.springframework.beans.factory.annotation.Value("${spring.application.name}") String nombreServicio) {
        this.jdbc = jdbc;
        this.json = json;
        this.tracer = tracer;
        this.nombreServicio = nombreServicio;
    }

    /**
     * @param agregado   nombre del agregado dueno del evento ("venta", "lote", ...)
     * @param agregadoId id de ese agregado
     * @param tipo       nombre del evento, que ademas es el topico destino
     * @param clave      localId: define la particion de Kafka y con eso el orden
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID registrar(String agregado, UUID agregadoId, String tipo, UUID clave, Object datos) {
        Sobre<Object> sobre = Sobre.de(tipo, nombreServicio, clave, traceIdActual(), datos);
        try {
            jdbc.sql(SQL)
                    .param(sobre.eventoId())
                    .param(agregado)
                    .param(agregadoId)
                    .param(tipo)
                    .param(clave.toString())
                    .param(json.writeValueAsString(sobre))
                    .update();
            return sobre.eventoId();
        } catch (Exception e) {
            // Si el evento no se puede serializar, la operacion de negocio tampoco debe
            // completarse: es preferible que la venta falle a que quede sin propagarse.
            throw new IllegalStateException("No se pudo registrar el evento " + tipo, e);
        }
    }

    private String traceIdActual() {
        Tracer t = tracer.getIfAvailable();
        if (t == null) {
            return "";
        }
        var span = t.currentSpan();
        return span == null ? "" : span.context().traceId();
    }
}

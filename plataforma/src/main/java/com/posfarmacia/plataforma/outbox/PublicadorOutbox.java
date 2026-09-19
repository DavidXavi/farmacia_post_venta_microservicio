package com.posfarmacia.plataforma.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empuja a Kafka lo que el outbox tiene pendiente.
 *
 * <p>Toma los pendientes con {@code FOR UPDATE SKIP LOCKED}, que es lo que permite
 * correr varias replicas del mismo servicio sin que dos publiquen el mismo evento:
 * cada pod se lleva un lote distinto y ninguno espera al otro.
 *
 * <p>ponytail: publicador por sondeo, no Debezium. Un sondeo cada 200 ms con lotes de
 * 200 filas aguanta con holgura los 200 eventos/s proyectados, y no agrega Kafka
 * Connect ni replicacion logica al stack. El techo esta medido y alertado: la metrica
 * {@code outbox.antiguedad.segundos} dispara alerta si el evento mas viejo pasa de
 * 60 s. Cuando esa alerta suene de forma sostenida, ahi entra Debezium sobre la
 * replicacion logica de Postgres, que es el siguiente escalon y no el primero.
 */
@Component
@ConditionalOnProperty(name = "pos.outbox.habilitado", havingValue = "true", matchIfMissing = true)
public class PublicadorOutbox {

    private static final Logger log = LoggerFactory.getLogger(PublicadorOutbox.class);

    private static final String PENDIENTES = """
            SELECT id, tipo, clave, payload, intentos
              FROM outbox
             WHERE publicado_en IS NULL
             ORDER BY creado_en
             LIMIT ?
             FOR UPDATE SKIP LOCKED
            """;

    private static final String MARCAR = "UPDATE outbox SET publicado_en = now() WHERE id = ANY (?)";
    private static final String FALLO = "UPDATE outbox SET intentos = intentos + 1 WHERE id = ?";
    private static final String ANTIGUEDAD = """
            SELECT COALESCE(EXTRACT(EPOCH FROM now() - MIN(creado_en)), 0)
              FROM outbox WHERE publicado_en IS NULL
            """;

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final int tamanoLote;
    private final AtomicLong antiguedadSegundos = new AtomicLong();

    public PublicadorOutbox(JdbcClient jdbc, KafkaTemplate<String, String> kafka,
            MeterRegistry metricas,
            @org.springframework.beans.factory.annotation.Value("${pos.outbox.tamano-lote:200}") int tamanoLote) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.tamanoLote = tamanoLote;
        // La metrica que decide cuando hay que cambiar a Debezium.
        metricas.gauge("outbox.antiguedad.segundos", antiguedadSegundos, AtomicLong::get);
    }

    record Pendiente(UUID id, String tipo, String clave, String payload, int intentos) {
    }

    @Scheduled(fixedDelayString = "${pos.outbox.intervalo-ms:200}")
    @Transactional
    public void publicar() {
        List<Pendiente> pendientes = jdbc.sql(PENDIENTES)
                .param(tamanoLote)
                .query(Pendiente.class)
                .list();

        if (pendientes.isEmpty()) {
            antiguedadSegundos.set(0);
            return;
        }

        var publicados = new java.util.ArrayList<UUID>(pendientes.size());
        for (Pendiente p : pendientes) {
            try {
                // get() con timeout: si Kafka no confirma, la fila se queda pendiente y
                // se reintenta en el proximo ciclo. Nunca se marca como publicado algo
                // que el broker no confirmo.
                kafka.send(p.tipo(), p.clave(), p.payload()).get(5, java.util.concurrent.TimeUnit.SECONDS);
                publicados.add(p.id());
            } catch (Exception e) {
                jdbc.sql(FALLO).param(p.id()).update();
                log.warn("Outbox: fallo al publicar {} (intento {}): {}",
                        p.id(), p.intentos() + 1, e.getMessage());
            }
        }

        if (!publicados.isEmpty()) {
            jdbc.sql(MARCAR).param(publicados.toArray(UUID[]::new)).update();
        }
    }

    /** Metrica de salud del outbox, separada del ciclo de publicacion para no encarecerlo. */
    @Scheduled(fixedDelay = 10_000)
    public void medirAntiguedad() {
        Double segundos = jdbc.sql(ANTIGUEDAD).query(Double.class).single();
        antiguedadSegundos.set(segundos == null ? 0L : segundos.longValue());
        if (antiguedadSegundos.get() > 60) {
            log.error("Outbox atrasado: el evento mas viejo lleva {} s sin publicar. "
                    + "Si esto se sostiene, toca pasar a Debezium.", antiguedadSegundos.get());
        }
    }

    Duration intervaloAlerta() {
        return Duration.ofSeconds(60);
    }

    Instant ahora() {
        return Instant.now();
    }
}

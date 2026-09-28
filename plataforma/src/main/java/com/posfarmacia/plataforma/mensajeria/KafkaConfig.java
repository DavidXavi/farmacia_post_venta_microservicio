package com.posfarmacia.plataforma.mensajeria;

import com.posfarmacia.contracts.Topicos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Lo que hace que un evento nunca se pierda del lado del consumidor.
 *
 * <p>Sin un manejador propio, Spring Kafka reintenta diez veces seguidas, sin espera, y
 * despues descarta el evento y avanza el offset. Un corte de un segundo en la base de
 * facturacion bastaba para que una venta se quedara sin comprobante para siempre, sin
 * error visible en ningun lado.
 *
 * <p>Ahora: seis reintentos con espera creciente (1, 2, 4, 8, 16 y 30 s, cerca de un
 * minuto en total) y, si aun asi falla, el evento se copia a {@code <topico>.dlq} con la
 * excepcion en los headers. Solo despues de eso avanza el offset.
 *
 * <p>ponytail: reintento bloqueante, no topicos de reintento. Mientras un evento se
 * reintenta, su particion espera hasta un minuto; las otras once siguen. Con la clave por
 * local eso es una botica demorada, nunca una perdida. Si un consumidor lento empieza a
 * atrasar particiones de forma sostenida, el siguiente escalon es
 * {@code @RetryableTopic}, que saca el evento de la particion mientras espera.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /** Particiones por topico. La clave es localId: mas particiones, mas locales en paralelo. */
    static final Map<String, Integer> PARTICIONES = new LinkedHashMap<>();

    static {
        PARTICIONES.put(Topicos.VENTAS_CONFIRMADAS, 12);
        PARTICIONES.put(Topicos.VENTAS_ANULADAS, 12);
        PARTICIONES.put(Topicos.LOTES_ASIGNADOS, 12);
        PARTICIONES.put(Topicos.AUDITORIA, 12);
        PARTICIONES.put(Topicos.STOCK_MOVIMIENTOS, 6);
        PARTICIONES.put(Topicos.COMPROBANTES_EMITIDOS, 6);
        PARTICIONES.put(Topicos.CATALOGO_CAMBIOS, 3);
    }

    /**
     * Crea los topicos al arrancar, o les agrega particiones si tienen menos. Sin esto,
     * Kafka los creaba solo con una particion la primera vez que alguien publicaba, y
     * toda la cadena procesaba las ventas de a una.
     *
     * <p>Sin replicas explicitas: toma las del broker (1 en local, 3 en produccion).
     */
    @Bean
    public KafkaAdmin.NewTopics topicos() {
        return new KafkaAdmin.NewTopics(listaTopicos().toArray(NewTopic[]::new));
    }

    static List<NewTopic> listaTopicos() {
        var lista = new ArrayList<NewTopic>();
        PARTICIONES.forEach((nombre, particiones) -> {
            lista.add(TopicBuilder.name(nombre).partitions(particiones).build());
            lista.add(TopicBuilder.name(nombre + Topicos.DLQ).partitions(1).build());
        });
        return lista;
    }

    @Bean
    public CommonErrorHandler manejadorErroresKafka(KafkaTemplate<String, String> kafka) {
        var aLaDlq = new DeadLetterPublishingRecoverer(kafka, KafkaConfig::destinoDlq);
        var espera = new ExponentialBackOffWithMaxRetries(6);
        espera.setInitialInterval(1_000);
        espera.setMultiplier(2.0);
        espera.setMaxInterval(30_000);

        var manejador = new DefaultErrorHandler(aLaDlq, espera);
        manejador.setRetryListeners((registro, error, intento) ->
                log.warn("Evento de {} fallo (intento {}), se reintenta: {}",
                        registro.topic(), intento, error.getMessage()));
        return manejador;
    }

    /** Particion -1: la elige Kafka. La cola muerta tiene una sola. */
    static TopicPartition destinoDlq(ConsumerRecord<?, ?> registro, Exception error) {
        log.error("Evento de {} agoto los reintentos, va a {}{}: {}",
                registro.topic(), registro.topic(), Topicos.DLQ, error.getMessage());
        return new TopicPartition(registro.topic() + Topicos.DLQ, -1);
    }
}

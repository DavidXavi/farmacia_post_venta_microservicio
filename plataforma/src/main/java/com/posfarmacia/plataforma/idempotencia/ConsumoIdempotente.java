package com.posfarmacia.plataforma.idempotencia;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ejecuta el efecto de un evento exactamente una vez.
 *
 * <p>Kafka entrega al-menos-una-vez. Un rebalanceo de particiones, un timeout de
 * commit o un reinicio del pod reentregan mensajes que ya se procesaron. Sin esta
 * guarda, un reenvio de {@code VentaConfirmada} descuenta el stock dos veces o cobra
 * dos veces el credito.
 *
 * <p>La guarda es un INSERT que choca con la PK compuesta (evento_id, consumidor).
 * Es el motor el que decide quien gana, no un {@code if (yaProcesado)} que dos pods
 * pueden evaluar a la vez y pasar los dos.
 *
 * <p>ponytail: no se usa exactly-once de Kafka. Cuesta rendimiento, complica el
 * consumidor y de todos modos no cubre el efecto secundario (la fila que ya se
 * escribio en Postgres). Consumidor idempotente resuelve el problema real.
 */
@Component
public class ConsumoIdempotente {

    private static final Logger log = LoggerFactory.getLogger(ConsumoIdempotente.class);

    private static final String MARCAR = """
            INSERT INTO evento_procesado (evento_id, consumidor) VALUES (?, ?)
            """;

    private final JdbcClient jdbc;

    public ConsumoIdempotente(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Corre {@code efecto} solo si este consumidor no vio antes este evento.
     *
     * <p>La marca y el efecto van en la MISMA transaccion. Si el efecto falla, la
     * marca se revierte con el, el evento queda sin procesar y el reintento de Kafka
     * lo vuelve a traer. Marcarlo antes en otra transaccion perderia el evento.
     *
     * @return true si se ejecuto, false si era un duplicado
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean unaVez(UUID eventoId, String consumidor, Runnable efecto) {
        try {
            jdbc.sql(MARCAR).param(eventoId).param(consumidor).update();
        } catch (DuplicateKeyException duplicado) {
            log.debug("Evento {} ya procesado por {}, se descarta", eventoId, consumidor);
            return false;
        }
        efecto.run();
        return true;
    }
}

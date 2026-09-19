package com.posfarmacia.facturacion.adapters.external;

import com.posfarmacia.facturacion.usecases.port.out.SunatPort;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulador de SUNAT que se comporta como SUNAT: lento y con fallas.
 *
 * <p>No es un mock que siempre devuelve OK. Un mock optimista haria que la demo
 * funcione perfecto y esconderia justo lo que hay que demostrar: que el sistema sigue
 * vendiendo cuando el organismo tributario no responde.
 *
 * <p>Latencia y tasa de falla se configuran por propiedad. Con
 * {@code SUNAT_TASA_FALLA=0.15} uno de cada siete envios falla y el comprobante entra
 * a la cadena de reintentos, que es el comportamiento que hay que mostrar en la
 * sustentacion.
 */
@Component
@ConditionalOnProperty(name = "pos.sunat.url", havingValue = "mock", matchIfMissing = true)
public class SunatMockAdapter implements SunatPort {

    private static final Logger log = LoggerFactory.getLogger(SunatMockAdapter.class);

    private final long latenciaMs;
    private final double tasaFalla;

    public SunatMockAdapter(@Value("${pos.sunat.latencia-ms:800}") long latenciaMs,
            @Value("${pos.sunat.tasa-falla:0.15}") double tasaFalla) {
        this.latenciaMs = latenciaMs;
        this.tasaFalla = tasaFalla;
        log.warn("SUNAT simulada: latencia {} ms, tasa de falla {}%",
                latenciaMs, Math.round(tasaFalla * 100));
    }

    @Override
    public Respuesta enviar(UUID comprobanteId, String tipo, String serie, int correlativo,
            BigDecimal montoTotal) {
        try {
            // La latencia es real: sin ella no se veria que este servicio necesita su
            // propio pool de hilos y su propio ritmo.
            Thread.sleep(latenciaMs + ThreadLocalRandom.current().nextLong(0, 400));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Respuesta(false, "INTERRUMPIDO", "Envio interrumpido");
        }

        if (ThreadLocalRandom.current().nextDouble() < tasaFalla) {
            return new Respuesta(false, "0100",
                    "El servicio de SUNAT no esta disponible, reintente mas tarde");
        }
        return new Respuesta(true, "0", "La " + tipo + " " + serie + "-" + correlativo
                + " ha sido aceptada");
    }
}

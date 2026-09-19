package com.posfarmacia.credito.adapters.messaging;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.posfarmacia.contracts.Sobre;
import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.VentaAnulada;
import com.posfarmacia.contracts.eventos.VentaConfirmada;
import com.posfarmacia.credito.usecases.port.out.CreditoPort;
import com.posfarmacia.plataforma.idempotencia.ConsumoIdempotente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierra y compensa la parte de credito de la saga.
 *
 * <p>Confirmada: la reserva se vuelve cargo firme y entra al ledger.
 * Anulada: se libera lo reservado, o se registra un abono si el cargo ya estaba hecho.
 */
@Component
public class VentaConsumer {

    private static final Logger log = LoggerFactory.getLogger(VentaConsumer.class);

    private final ObjectMapper json;
    private final ConsumoIdempotente idempotencia;
    private final CreditoPort credito;

    public VentaConsumer(ObjectMapper json, ConsumoIdempotente idempotencia, CreditoPort credito) {
        this.json = json;
        this.idempotencia = idempotencia;
        this.credito = credito;
    }

    @KafkaListener(topics = Topicos.VENTAS_CONFIRMADAS, groupId = "ms-credito")
    @Transactional
    public void confirmada(String mensaje, Acknowledgment ack) throws Exception {
        Sobre<VentaConfirmada> sobre = json.readValue(mensaje, new TypeReference<>() {
        });
        VentaConfirmada v = sobre.datos();

        // Solo interesa si la venta uso credito. El resto de ventas pasa de largo.
        if (v.lineaCreditoId() == null) {
            ack.acknowledge();
            return;
        }

        idempotencia.unaVez(sobre.eventoId(), "ms-credito.venta-confirmada", () -> {
            credito.confirmarCargo(v.lineaCreditoId(), v.ventaId(), v.total());
            credito.reservaDeVenta(v.ventaId())
                    .ifPresent(r -> credito.cerrarReserva(r, "CONFIRMADA"));
            log.info("Cargo de credito firme para la venta {}: {}", v.ventaId(), v.total());
        });
        ack.acknowledge();
    }

    @KafkaListener(topics = Topicos.VENTAS_ANULADAS, groupId = "ms-credito")
    @Transactional
    public void anulada(String mensaje, Acknowledgment ack) throws Exception {
        Sobre<VentaAnulada> sobre = json.readValue(mensaje, new TypeReference<>() {
        });
        VentaAnulada v = sobre.datos();

        idempotencia.unaVez(sobre.eventoId(), "ms-credito.venta-anulada", () ->
                credito.reservaDeVenta(v.ventaId()).ifPresent(reservaId -> {
                    credito.cerrarReserva(reservaId, "LIBERADA");
                    log.warn("Credito liberado por anulacion de la venta {}", v.ventaId());
                }));
        ack.acknowledge();
    }
}

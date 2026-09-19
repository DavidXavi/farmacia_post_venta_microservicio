package com.posfarmacia.credito.usecases.usecase;

import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.OperacionAuditada;
import com.posfarmacia.credito.usecases.port.out.CreditoEscrituraPort;
import com.posfarmacia.credito.usecases.port.out.CreditoPort;
import com.posfarmacia.plataforma.outbox.OutboxRegistrador;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Otorgar o ajustar la linea de credito de un cliente.
 *
 * <p>Es dinero, asi que deja dos rastros ademas del cambio: una fila en el ledger
 * append-only y un evento de auditoria. Cualquiera de los dos por separado se puede
 * discutir; los dos juntos dicen quien autorizo cuanto y cuando.
 */
@Service
public class OtorgarCreditoUseCase {

    private static final Logger log = LoggerFactory.getLogger(OtorgarCreditoUseCase.class);

    /** Tope duro por cliente. Un cero de mas en el formulario no puede pasar callado. */
    private static final BigDecimal TOPE = new BigDecimal("50000.00");

    private final CreditoEscrituraPort escritura;
    private final CreditoPort credito;
    private final OutboxRegistrador outbox;
    private final Clock reloj;

    public OtorgarCreditoUseCase(CreditoEscrituraPort escritura, CreditoPort credito,
            OutboxRegistrador outbox, Clock reloj) {
        this.escritura = escritura;
        this.credito = credito;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public UUID otorgar(UUID clienteId, BigDecimal montoAutorizado, LocalDate inicio,
            LocalDate fin, UUID autorizadorId) {
        if (montoAutorizado == null || montoAutorizado.signum() <= 0) {
            throw new IllegalArgumentException("El monto autorizado tiene que ser mayor que cero");
        }
        if (montoAutorizado.compareTo(TOPE) > 0) {
            throw new IllegalArgumentException(
                    "El monto supera el tope por cliente de S/ " + TOPE
                            + ". Si es correcto, tiene que autorizarlo la gerencia.");
        }
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new IllegalArgumentException("La vigencia termina antes de empezar");
        }

        BigDecimal anterior = credito.lineaDe(clienteId)
                .map(CreditoPort.Linea::montoAutorizado)
                .orElse(null);

        UUID lineaId = escritura.guardarLinea(clienteId, montoAutorizado, inicio, fin);
        escritura.registrarMovimiento(lineaId, "AJUSTE", montoAutorizado, montoAutorizado);

        outbox.registrar("linea-credito", lineaId, Topicos.AUDITORIA, lineaId,
                new OperacionAuditada(autorizadorId, "ms-credito",
                        anterior == null ? "ALTA_LINEA_CREDITO" : "AJUSTE_LINEA_CREDITO",
                        "linea_credito", lineaId.toString(),
                        "Cliente " + clienteId,
                        anterior == null ? null : anterior.toPlainString(),
                        montoAutorizado.toPlainString(), null, Instant.now(reloj)));

        log.warn("Linea de credito del cliente {}: {} -> {} autorizado por {}",
                clienteId, anterior, montoAutorizado, autorizadorId);
        return lineaId;
    }
}

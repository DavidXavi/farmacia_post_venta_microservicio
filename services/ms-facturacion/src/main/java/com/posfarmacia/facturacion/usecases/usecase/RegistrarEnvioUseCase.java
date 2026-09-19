package com.posfarmacia.facturacion.usecases.usecase;

import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.ComprobanteEmitido;
import com.posfarmacia.facturacion.usecases.port.out.ComprobantePort;
import com.posfarmacia.facturacion.usecases.port.out.SunatPort;
import com.posfarmacia.plataforma.outbox.OutboxRegistrador;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda el resultado de un envío a SUNAT.
 *
 * <p>Es un bean aparte de {@link EmitirComprobanteUseCase} a propósito, y la razón es
 * sutil pero rompe el sistema si se ignora: Spring implementa {@code @Transactional}
 * con un proxy, y una llamada de un método a otro del MISMO bean no pasa por el proxy.
 * Si esto viviera dentro del caso de uso que hace el envío, la anotación no se
 * aplicaría, no habría transacción, y {@code OutboxRegistrador} (que exige una con
 * {@code MANDATORY}) fallaría en cada comprobante aceptado.
 *
 * <p>La otra opción era poner {@code @Transactional} en el método que recorre el lote,
 * pero eso dejaría la llamada a SUNAT (hasta 15 s) dentro de la transacción,
 * sosteniendo una conexión de base de datos mientras se espera a un tercero lento.
 * Eso es justamente lo que esta arquitectura existe para evitar.
 */
@Service
public class RegistrarEnvioUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarEnvioUseCase.class);

    private final ComprobantePort comprobantes;
    private final OutboxRegistrador outbox;

    public RegistrarEnvioUseCase(ComprobantePort comprobantes, OutboxRegistrador outbox) {
        this.comprobantes = comprobantes;
        this.outbox = outbox;
    }

    @Transactional
    public void registrar(ComprobantePort.Pendiente c, SunatPort.Respuesta respuesta) {
        comprobantes.registrarEnvio(c.id(), respuesta.aceptado(), respuesta.codigo(),
                respuesta.mensaje());

        if (!respuesta.aceptado()) {
            // No se pierde nada: sigue PENDIENTE y el próximo ciclo lo reintenta. La
            // alerta de comprobantes pendientes avisa si esto deja de ser transitorio.
            log.warn("SUNAT rechazó el comprobante {}-{}: {}",
                    c.serie(), c.correlativo(), respuesta.mensaje());
            return;
        }

        comprobantes.marcarAceptado(c.id());
        outbox.registrar("comprobante", c.id(), Topicos.COMPROBANTES_EMITIDOS, c.localId(),
                new ComprobanteEmitido(c.id(), c.ventaId(), c.localId(), c.tipo(), c.serie(),
                        c.correlativo(), "ACEPTADO", Instant.now()));

        log.info("Comprobante {}-{} aceptado por SUNAT", c.serie(), c.correlativo());
    }
}

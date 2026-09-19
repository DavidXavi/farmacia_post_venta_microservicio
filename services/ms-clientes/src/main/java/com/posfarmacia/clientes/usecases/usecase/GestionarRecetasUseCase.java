package com.posfarmacia.clientes.usecases.usecase;

import com.posfarmacia.clientes.usecases.port.out.ClientesEscrituraPort;
import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.OperacionAuditada;
import com.posfarmacia.plataforma.outbox.OutboxRegistrador;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recetas: alta y validacion del quimico farmaceutico.
 *
 * <p>Una receta nace PENDIENTE y solo el quimico la pasa a APROBADA o RECHAZADA. Esa
 * separacion es el control: quien atiende en mostrador carga el papel, quien tiene el
 * titulo decide si vale. Si el alta naciera aprobada, el control no existiria.
 *
 * <p>La validacion se audita. Es la operacion que un inspector va a querer rastrear:
 * quien aprobo la receta con la que salio un controlado.
 */
@Service
public class GestionarRecetasUseCase {

    private static final Logger log = LoggerFactory.getLogger(GestionarRecetasUseCase.class);

    private static final String PENDIENTE = "PENDIENTE";
    private static final String APROBADA = "APROBADA";
    private static final String RECHAZADA = "RECHAZADA";

    private final ClientesEscrituraPort escritura;
    private final OutboxRegistrador outbox;
    private final Clock reloj;

    public GestionarRecetasUseCase(ClientesEscrituraPort escritura, OutboxRegistrador outbox,
            Clock reloj) {
        this.escritura = escritura;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    public record AltaReceta(String numero, String tipo, LocalDate fechaEmision,
                             LocalDate fechaVencimiento, UUID productoId, UUID clienteId,
                             String datosPaciente, String datosProfesional, String dosis,
                             int cantidadAutorizada, String archivoRespaldoUrl) {
    }

    @Transactional
    public UUID registrar(AltaReceta alta) {
        String numero = exigir(alta.numero(), "numero de receta");
        if (escritura.existeNumeroReceta(numero)) {
            throw new IllegalStateException("Ya existe la receta numero " + numero);
        }
        if (alta.cantidadAutorizada() <= 0) {
            throw new IllegalArgumentException("La cantidad autorizada tiene que ser mayor que cero");
        }
        if (alta.fechaEmision() == null) {
            throw new IllegalArgumentException("La receta necesita fecha de emision");
        }
        // Una receta emitida en el futuro es un error de tipeo o una receta falsificada.
        // En los dos casos, no se acepta.
        if (alta.fechaEmision().isAfter(LocalDate.now(reloj))) {
            throw new IllegalArgumentException(
                    "La fecha de emision no puede ser futura: " + alta.fechaEmision());
        }
        if (alta.fechaVencimiento() != null
                && alta.fechaVencimiento().isBefore(alta.fechaEmision())) {
            throw new IllegalArgumentException("La receta vence antes de emitirse");
        }

        UUID id = UUID.randomUUID();
        escritura.insertarReceta(new ClientesEscrituraPort.NuevaReceta(id, numero,
                alta.tipo() == null ? "NORMAL" : alta.tipo(), alta.fechaEmision(),
                alta.fechaVencimiento(), alta.productoId(), alta.clienteId(),
                exigir(alta.datosPaciente(), "datos del paciente"),
                exigir(alta.datosProfesional(), "datos del profesional"),
                alta.dosis(), alta.cantidadAutorizada(), alta.archivoRespaldoUrl()));
        return id;
    }

    /** La decision del quimico. Solo se puede tomar una vez. */
    @Transactional
    public String validar(UUID recetaId, UUID validadorId, boolean aprobar, String observaciones) {
        String estado = escritura.estadoReceta(recetaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la receta " + recetaId));

        if (!PENDIENTE.equals(estado)) {
            throw new IllegalStateException(
                    "La receta ya fue revisada: esta " + estado);
        }

        String nuevo = aprobar ? APROBADA : RECHAZADA;
        escritura.cambiarEstadoReceta(recetaId, nuevo);

        outbox.registrar("receta", recetaId, Topicos.AUDITORIA, recetaId,
                new OperacionAuditada(validadorId, "ms-clientes", "VALIDACION_RECETA",
                        "receta", recetaId.toString(),
                        observaciones == null || observaciones.isBlank()
                                ? "Sin observaciones" : observaciones,
                        PENDIENTE, nuevo, null, Instant.now(reloj)));

        log.info("Receta {} {} por {}", recetaId, nuevo, validadorId);
        return nuevo;
    }

    private String exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + campo);
        }
        return valor.trim();
    }
}

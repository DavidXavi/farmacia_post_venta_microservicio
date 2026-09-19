package com.posfarmacia.promociones.usecases.usecase;

import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.OperacionAuditada;
import com.posfarmacia.plataforma.outbox.OutboxRegistrador;
import com.posfarmacia.promociones.usecases.port.out.PromocionEscrituraPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga y baja de promociones.
 *
 * <p>Valida el tipo de beneficio contra la lista que el dominio sabe calcular. No es
 * burocracia: un tipo que {@code ReglaPromocion} no reconoce descuenta cero, la
 * promocion queda cargada, visible en la pantalla y sin ningun efecto en la caja. Es
 * el fallo mas caro de esta pantalla porque no falla nada, solo no descuenta, y nadie
 * lo nota hasta que un cliente reclama.
 */
@Service
public class AdministrarPromocionesUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdministrarPromocionesUseCase.class);

    /** Los tres que el dominio sabe calcular. Cualquier otro descontaria cero. */
    private static final Set<String> TIPOS = Set.of(
            "DESCUENTO_PORCENTAJE", "DESCUENTO_MONTO", "LLEVA_N_PAGA_M");

    private final PromocionEscrituraPort escritura;
    private final OutboxRegistrador outbox;
    private final Clock reloj;

    public AdministrarPromocionesUseCase(PromocionEscrituraPort escritura,
            OutboxRegistrador outbox, Clock reloj) {
        this.escritura = escritura;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    public record AltaPromocion(String nombre, String descripcion, String tipoBeneficio,
                                BigDecimal valorBeneficio, boolean requiereCliente,
                                int cantidadMinima, LocalDate vigenciaInicio,
                                LocalDate vigenciaFin, List<UUID> productosParticipantes) {
    }

    @Transactional
    public UUID registrar(AltaPromocion alta) {
        UUID id = UUID.randomUUID();
        escritura.insertar(id, validar(alta), productosDe(alta));
        log.info("Promocion {} cargada: {} de {}", id, alta.tipoBeneficio(), alta.valorBeneficio());
        return id;
    }

    @Transactional
    public void actualizar(UUID id, AltaPromocion cambio) {
        if (!escritura.actualizar(id, validar(cambio), productosDe(cambio))) {
            throw new IllegalArgumentException("No existe la promocion " + id);
        }
    }

    @Transactional
    public void desactivar(UUID id, UUID usuarioId) {
        boolean activa = escritura.estaActiva(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la promocion " + id));
        if (!activa) {
            return;   // idempotente: desactivar lo ya desactivado no es un error
        }
        escritura.desactivar(id);

        // Se audita porque cambia lo que paga el cliente, igual que un cambio de lista:
        // alguien va a preguntar por que dejo de aplicarse el descuento y cuando.
        outbox.registrar("promocion", id, Topicos.AUDITORIA, id,
                new OperacionAuditada(usuarioId, "ms-promociones", "DESACTIVAR_PROMOCION",
                        "promocion", id.toString(), "Desactivada desde la pantalla",
                        "activa", "inactiva", null, Instant.now(reloj)));
        log.warn("Promocion {} desactivada por {}", id, usuarioId);
    }

    private PromocionEscrituraPort.DatosPromocion validar(AltaPromocion p) {
        if (p.nombre() == null || p.nombre().isBlank()) {
            throw new IllegalArgumentException("La promocion necesita un nombre");
        }
        if (!TIPOS.contains(p.tipoBeneficio())) {
            throw new IllegalArgumentException("Tipo de beneficio desconocido: "
                    + p.tipoBeneficio() + ". Los validos son " + TIPOS);
        }
        if (p.valorBeneficio() == null || p.valorBeneficio().signum() <= 0) {
            throw new IllegalArgumentException("El valor del beneficio tiene que ser mayor que cero");
        }
        if ("DESCUENTO_PORCENTAJE".equals(p.tipoBeneficio())
                && p.valorBeneficio().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Un descuento no puede pasar de 100 por ciento");
        }
        if (p.cantidadMinima() < 1) {
            throw new IllegalArgumentException("La cantidad minima tiene que ser al menos 1");
        }
        if (p.vigenciaInicio() != null && p.vigenciaFin() != null
                && p.vigenciaFin().isBefore(p.vigenciaInicio())) {
            throw new IllegalArgumentException("La vigencia termina antes de empezar");
        }
        if (productosDe(p).isEmpty()) {
            throw new IllegalArgumentException(
                    "Una promocion sin productos no aplica a nada: elige al menos uno");
        }
        return new PromocionEscrituraPort.DatosPromocion(p.nombre().trim(), p.descripcion(),
                p.tipoBeneficio(), p.valorBeneficio(), p.requiereCliente(),
                p.cantidadMinima(), p.vigenciaInicio(), p.vigenciaFin());
    }

    private List<UUID> productosDe(AltaPromocion p) {
        return p.productosParticipantes() == null ? List.of()
                : p.productosParticipantes().stream().distinct().toList();
    }
}

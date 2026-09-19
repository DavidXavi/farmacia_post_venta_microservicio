package com.posfarmacia.inventario.adapters.web;

import com.posfarmacia.inventario.usecases.usecase.AdministrarLotesUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recepcion de mercaderia y baja de lotes.
 *
 * <p>Exige rol de inventario o administracion: quien esta en caja no puede dar de alta
 * stock que nunca llego.
 *
 * <p>El usuario sale del token, nunca del cuerpo. Un bloqueo de lote firmado con el id
 * que mande el cliente no sirve de nada en una auditoria.
 */
@RestController
@RequestMapping("/api/lotes")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'ENCARGADO_INVENTARIO', 'OPERADOR_CENTRAL')")
public class LotesController {

    public record LotePeticion(
            @NotBlank String codigo,
            @NotNull UUID productoId,
            @NotNull UUID localId,
            @NotNull LocalDate fechaVencimiento,
            @Positive int cantidadRecibida,
            BigDecimal costo) {
    }

    /** El motivo es opcional en el cuerpo, pero nunca queda vacio en la auditoria. */
    public record MotivoPeticion(String motivo) {
    }

    private final AdministrarLotesUseCase lotes;

    public LotesController(AdministrarLotesUseCase lotes) {
        this.lotes = lotes;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> registrar(@Valid @RequestBody LotePeticion p,
            @AuthenticationPrincipal Jwt jwt) {
        UUID id = lotes.registrar(new AdministrarLotesUseCase.AltaLote(p.codigo(),
                p.productoId(), p.localId(), p.fechaVencimiento(), p.cantidadRecibida(),
                p.costo()), usuarioDe(jwt));
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PatchMapping("/{id}/bloquear")
    public ResponseEntity<Void> bloquear(@PathVariable UUID id,
            @RequestBody(required = false) MotivoPeticion p, @AuthenticationPrincipal Jwt jwt) {
        lotes.bloquear(id, usuarioDe(jwt), motivoDe(p, "Bloqueado desde la pantalla de lotes"));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/retirar")
    public ResponseEntity<Void> retirar(@PathVariable UUID id,
            @RequestBody(required = false) MotivoPeticion p, @AuthenticationPrincipal Jwt jwt) {
        lotes.retirar(id, usuarioDe(jwt), motivoDe(p, "Retirado desde la pantalla de lotes"));
        return ResponseEntity.noContent().build();
    }

    private String motivoDe(MotivoPeticion p, String porDefecto) {
        return p == null || p.motivo() == null || p.motivo().isBlank() ? porDefecto : p.motivo();
    }

    private UUID usuarioDe(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

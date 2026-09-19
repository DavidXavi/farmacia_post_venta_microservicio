package com.posfarmacia.credito.adapters.web;

import com.posfarmacia.credito.usecases.usecase.OtorgarCreditoUseCase;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Otorgar credito a un cliente.
 *
 * <p>Solo administracion. Es la unica pantalla del sistema donde alguien decide cuanto
 * dinero se le fia a un cliente, y por eso queda auditada con el usuario del token.
 */
@RestController
@RequestMapping("/api/lineas-credito")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR_CENTRAL')")
public class LineasCreditoController {

    public record LineaPeticion(@NotNull UUID clienteId,
                                @NotNull @Positive BigDecimal montoAutorizado,
                                LocalDate vigenciaInicio,
                                LocalDate vigenciaFin) {
    }

    private final OtorgarCreditoUseCase otorgar;

    public LineasCreditoController(OtorgarCreditoUseCase otorgar) {
        this.otorgar = otorgar;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> otorgar(@Valid @RequestBody LineaPeticion p,
            @AuthenticationPrincipal Jwt jwt) {
        UUID id = otorgar.otorgar(p.clienteId(), p.montoAutorizado(), p.vigenciaInicio(),
                p.vigenciaFin(), UUID.fromString(jwt.getSubject()));
        return ResponseEntity.status(201).body(Map.of(
                "id", id,
                "clienteId", p.clienteId(),
                "montoAutorizado", p.montoAutorizado(),
                "saldoDisponible", p.montoAutorizado(),
                "reservado", BigDecimal.ZERO,
                "estado", "ACTIVA"));
    }
}

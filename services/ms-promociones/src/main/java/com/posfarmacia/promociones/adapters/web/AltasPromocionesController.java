package com.posfarmacia.promociones.adapters.web;

import com.posfarmacia.promociones.usecases.usecase.AdministrarPromocionesUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Carga y baja de promociones.
 *
 * <p>Solo administracion: una promocion mal cargada cambia lo que paga cada cliente de
 * la cadena hasta que alguien la note.
 */
@RestController
@RequestMapping("/api/promociones")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR_CENTRAL')")
public class AltasPromocionesController {

    public record PromocionPeticion(
            @NotBlank String nombre,
            String descripcion,
            @NotBlank String tipoBeneficio,
            @NotNull BigDecimal valorBeneficio,
            boolean requiereCliente,
            int cantidadMinima,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFin,
            List<UUID> productosParticipantes) {
    }

    private final AdministrarPromocionesUseCase promociones;

    public AltasPromocionesController(AdministrarPromocionesUseCase promociones) {
        this.promociones = promociones;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> registrar(@Valid @RequestBody PromocionPeticion p) {
        return ResponseEntity.status(201).body(Map.of("id", promociones.registrar(alta(p))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(@PathVariable UUID id,
            @Valid @RequestBody PromocionPeticion p) {
        promociones.actualizar(id, alta(p));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        promociones.desactivar(id, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    private AdministrarPromocionesUseCase.AltaPromocion alta(PromocionPeticion p) {
        return new AdministrarPromocionesUseCase.AltaPromocion(p.nombre(), p.descripcion(),
                p.tipoBeneficio(), p.valorBeneficio(), p.requiereCliente(),
                p.cantidadMinima(), p.vigenciaInicio(), p.vigenciaFin(),
                p.productosParticipantes());
    }
}

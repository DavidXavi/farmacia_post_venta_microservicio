package com.posfarmacia.identidad.adapters.web;

import com.posfarmacia.identidad.usecases.usecase.AdministrarAccesoUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta de usuarios y locales. Solo administracion, por razones obvias.
 */
@RestController
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdministracionController {

    public record UsuarioPeticion(@NotBlank String nombreUsuario, @NotBlank String password,
                                  @NotNull UUID localId, List<String> roles) {
    }

    public record LocalPeticion(@NotBlank String nombre, String direccion) {
    }

    private final AdministrarAccesoUseCase acceso;

    public AdministracionController(AdministrarAccesoUseCase acceso) {
        this.acceso = acceso;
    }

    @PostMapping("/api/usuarios")
    public ResponseEntity<Map<String, Object>> usuario(@Valid @RequestBody UsuarioPeticion p,
            @AuthenticationPrincipal Jwt jwt) {
        UUID id = acceso.registrarUsuario(p.nombreUsuario(), p.password(), p.localId(),
                p.roles() == null ? List.of() : p.roles(), UUID.fromString(jwt.getSubject()));
        return ResponseEntity.status(201).body(Map.of(
                "id", id, "nombreUsuario", p.nombreUsuario(), "estado", "ACTIVO"));
    }

    @PostMapping("/api/locales")
    public ResponseEntity<Map<String, Object>> local(@Valid @RequestBody LocalPeticion p) {
        UUID id = acceso.registrarLocal(p.nombre(), p.direccion());
        return ResponseEntity.status(201).body(Map.of("id", id, "nombre", p.nombre()));
    }
}

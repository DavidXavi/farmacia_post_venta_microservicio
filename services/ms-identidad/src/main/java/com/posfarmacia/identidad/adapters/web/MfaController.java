package com.posfarmacia.identidad.adapters.web;

import com.posfarmacia.identidad.usecases.usecase.GestionarMfaUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta y baja del segundo factor del usuario que esta en sesion.
 *
 * <p>Separado de {@link AuthController} porque son dos cosas distintas: aquel atiende
 * a quien todavia no tiene token y es publico; este exige sesion y opera siempre sobre
 * la cuenta del propio token. Que el usuario salga del JWT y no del cuerpo no es un
 * detalle: si viniera en el cuerpo, cualquiera con sesion podria apagarle el segundo
 * factor a otro.
 */
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    public record EstadoVista(boolean habilitado) {
    }

    public record CodigoPeticion(@NotBlank String codigo) {
    }

    private final GestionarMfaUseCase mfa;

    public MfaController(GestionarMfaUseCase mfa) {
        this.mfa = mfa;
    }

    @GetMapping("/estado")
    public ResponseEntity<EstadoVista> estado(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new EstadoVista(mfa.habilitado(usuarioDe(jwt))));
    }

    @PostMapping("/registro")
    public ResponseEntity<GestionarMfaUseCase.Registro> registrar(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(mfa.iniciarRegistro(usuarioDe(jwt)));
    }

    @PostMapping("/registro/confirmar")
    public ResponseEntity<EstadoVista> confirmar(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CodigoPeticion p) {
        mfa.confirmarRegistro(usuarioDe(jwt), p.codigo());
        return ResponseEntity.ok(new EstadoVista(true));
    }

    @DeleteMapping
    public ResponseEntity<EstadoVista> deshabilitar(@AuthenticationPrincipal Jwt jwt) {
        mfa.deshabilitar(usuarioDe(jwt));
        return ResponseEntity.ok(new EstadoVista(false));
    }

    private UUID usuarioDe(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

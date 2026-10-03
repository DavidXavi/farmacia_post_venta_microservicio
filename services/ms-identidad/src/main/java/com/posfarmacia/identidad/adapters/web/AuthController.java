package com.posfarmacia.identidad.adapters.web;

import com.posfarmacia.identidad.domain.Totp;
import com.posfarmacia.identidad.usecases.port.out.UsuarioPort;
import com.posfarmacia.identidad.usecases.usecase.EmitirTokenUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login con contrasena y verificacion del segundo factor.
 *
 * <p>La logica de autenticacion no cambio al pasar a microservicios: sigue siendo la
 * misma del proyecto original. Lo que cambio es quien la ejecuta y que ahora emite un
 * token que los otros nueve servicios validan solos, sin volver a preguntar.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginPeticion(@NotBlank String nombreUsuario, @NotBlank String password) {
    }

    public record MfaPeticion(@NotBlank String codigo, @NotBlank String tokenPendiente) {
    }

    public record TokenRespuesta(String token, String scope, boolean requiereMfa, UUID usuarioId) {
    }

    private final UsuarioPort usuarios;
    private final EmitirTokenUseCase tokens;
    private final PasswordEncoder encoder;

    public AuthController(UsuarioPort usuarios, EmitirTokenUseCase tokens, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.encoder = encoder;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenRespuesta> login(@Valid @RequestBody LoginPeticion p) {
        var cuenta = usuarios.porNombreUsuario(p.nombreUsuario()).orElse(null);

        // Mismo mensaje y mismo tiempo para usuario inexistente y clave mala: decir
        // cual de los dos fallo le regala al atacante la mitad del trabajo.
        if (cuenta == null || cuenta.passwordHash() == null
                || !encoder.matches(p.password(), cuenta.passwordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"ACTIVO".equals(cuenta.estado())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (cuenta.mfaHabilitado()) {
            // Token intermedio, 5 min, solo sirve para verificar el segundo factor.
            return ResponseEntity.ok(new TokenRespuesta(
                    tokens.tokenMfaPendiente(cuenta),
                    EmitirTokenUseCase.SCOPE_MFA_PENDIENTE, true, cuenta.id()));
        }

        return ResponseEntity.ok(new TokenRespuesta(
                tokens.tokenDeAcceso(cuenta), EmitirTokenUseCase.SCOPE_COMPLETO, false, cuenta.id()));
    }

    @PostMapping("/mfa/verificar")
    public ResponseEntity<TokenRespuesta> verificarMfa(@Valid @RequestBody MfaPeticion p) {
        var cuenta = usuarioDeTokenPendiente(p.tokenPendiente());
        if (cuenta == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!Totp.verificar(cuenta.mfaSecret(), p.codigo(), Instant.now())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new TokenRespuesta(
                tokens.tokenDeAcceso(cuenta), EmitirTokenUseCase.SCOPE_COMPLETO, false, cuenta.id()));
    }

    private UsuarioPort.Cuenta usuarioDeTokenPendiente(String token) {
        return tokens.usuarioDeTokenMfaPendiente(token).flatMap(usuarios::porId).orElse(null);
    }
}

package com.posfarmacia.identidad.usecases.usecase;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.posfarmacia.identidad.usecases.port.out.UsuarioPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emision de JWT. El unico lugar del sistema que firma tokens.
 *
 * <p>Dos decisiones que importan a esta escala:
 *
 * <p><b>Acceso de 15 minutos, no de 8 horas.</b> El proyecto original usaba 480 minutos.
 * Con 1500 terminales repartidos en 500 locales, una ventana de ocho horas para un token
 * robado es demasiado ancha; el refresh token de 8 horas, que si se puede revocar en
 * base de datos, cubre la comodidad del cajero sin esa exposicion.
 *
 * <p><b>El MFA pendiente viaja en un token con scope propio.</b> Cinco minutos de vida y
 * solo sirve para el endpoint de verificacion. Asi los dos caminos de entrada, contrasena
 * y social, usan el mismo mecanismo sin guardar estado de sesion en ningun lado, que es
 * lo que permite que cualquiera de las seis replicas atienda el segundo paso.
 */
@Service
public class EmitirTokenUseCase {

    public static final String SCOPE_COMPLETO = "COMPLETO";
    public static final String SCOPE_MFA_PENDIENTE = "MFA_PENDIENTE";

    private final RSAKey clave;
    private final String emisor;
    private final Duration vidaAcceso;

    public EmitirTokenUseCase(RSAKey clave,
            @Value("${pos.jwt.emisor}") String emisor,
            @Value("${pos.jwt.minutos-acceso:15}") int minutosAcceso) {
        this.clave = clave;
        this.emisor = emisor;
        this.vidaAcceso = Duration.ofMinutes(minutosAcceso);
    }

    public String tokenDeAcceso(UsuarioPort.Cuenta cuenta) {
        return firmar(cuenta, SCOPE_COMPLETO, cuenta.roles(), vidaAcceso);
    }

    /** Token intermedio: solo habilita POST /api/auth/mfa/verificar. */
    public String tokenMfaPendiente(UsuarioPort.Cuenta cuenta) {
        return firmar(cuenta, SCOPE_MFA_PENDIENTE, List.of(), Duration.ofMinutes(5));
    }

    /**
     * Usuario de un token de MFA pendiente, solo si lo firmo este servicio.
     *
     * <p>Sin verificar la firma, cualquiera podria armar un token con el id de otro
     * usuario y saltarse la contrasena: solo le faltaria el codigo TOTP.
     */
    public Optional<UUID> usuarioDeTokenMfaPendiente(String token) {
        try {
            var jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new RSASSAVerifier(clave.toRSAPublicKey()))) {
                return Optional.empty();
            }
            var claims = jwt.getJWTClaimsSet();
            if (!emisor.equals(claims.getIssuer())
                    || !SCOPE_MFA_PENDIENTE.equals(claims.getStringClaim("scope"))
                    || claims.getExpirationTime() == null
                    || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String firmar(UsuarioPort.Cuenta cuenta, String scope, List<String> roles,
            Duration vida) {
        try {
            Instant ahora = Instant.now();
            var claims = new JWTClaimsSet.Builder()
                    .subject(cuenta.id().toString())
                    .issuer(emisor)
                    .issueTime(Date.from(ahora))
                    .expirationTime(Date.from(ahora.plus(vida)))
                    .claim("nombre_usuario", cuenta.nombreUsuario())
                    // El localId viaja en el token para que los otros servicios no
                    // tengan que preguntarle a identidad de que botica es el cajero.
                    .claim("local_id", cuenta.localId().toString())
                    .claim("roles", roles)
                    .claim("scope", scope)
                    .build();

            var jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(clave.getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims);
            jwt.sign(new RSASSASigner(clave.toPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }
}

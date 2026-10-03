package com.posfarmacia.identidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.posfarmacia.identidad.usecases.port.out.UsuarioPort;
import com.posfarmacia.identidad.usecases.usecase.EmitirTokenUseCase;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El token de MFA pendiente solo vale si lo firmo identidad: si no, se salta la contrasena. */
class TokenMfaPendienteTest {

    private static final String EMISOR = "https://pos-farmacia.local";

    private final RSAKey clave = generar("pos-1");
    private final EmitirTokenUseCase tokens = new EmitirTokenUseCase(clave, EMISOR, 15);
    private final UsuarioPort.Cuenta cuenta = new UsuarioPort.Cuenta(UUID.randomUUID(), "cajero",
            "hash", "ACTIVO", UUID.randomUUID(), "c@b.pe", "SECRETO", true, List.of("CAJERO"));

    @Test
    void aceptaElTokenQueEmitioIdentidad() {
        assertEquals(Optional.of(cuenta.id()),
                tokens.usuarioDeTokenMfaPendiente(tokens.tokenMfaPendiente(cuenta)));
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() throws Exception {
        String falso = firmarCon(generar("pos-1"), claimsPendientes(cuenta.id()));
        assertTrue(tokens.usuarioDeTokenMfaPendiente(falso).isEmpty());
    }

    @Test
    void rechazaUnTokenSinFirma() {
        String sinFirma = new PlainJWT(claimsPendientes(cuenta.id())).serialize();
        assertTrue(tokens.usuarioDeTokenMfaPendiente(sinFirma).isEmpty());
    }

    @Test
    void rechazaUnTokenLegitimoConLosClaimsCambiados() throws Exception {
        var partes = SignedJWT.parse(tokens.tokenMfaPendiente(cuenta)).getParsedParts();
        String otroUsuario = Base64URL.encode(claimsPendientes(UUID.randomUUID()).toString()).toString();
        String alterado = partes[0] + "." + otroUsuario + "." + partes[2];
        assertTrue(tokens.usuarioDeTokenMfaPendiente(alterado).isEmpty());
    }

    @Test
    void rechazaElTokenDeAccesoCompleto() {
        assertTrue(tokens.usuarioDeTokenMfaPendiente(tokens.tokenDeAcceso(cuenta)).isEmpty());
    }

    private static JWTClaimsSet claimsPendientes(UUID usuario) {
        return new JWTClaimsSet.Builder()
                .subject(usuario.toString())
                .issuer(EMISOR)
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("scope", EmitirTokenUseCase.SCOPE_MFA_PENDIENTE)
                .build();
    }

    private static String firmarCon(RSAKey clave, JWTClaimsSet claims) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(clave.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(clave.toPrivateKey()));
        return jwt.serialize();
    }

    private static RSAKey generar(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

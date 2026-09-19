package com.posfarmacia.identidad.infrastructure;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Par de claves RSA con el que ms-identidad firma los JWT.
 *
 * <p>RS256 y no HS256. Con clave simetrica, cada servicio necesitaria el secreto para
 * validar, y un secreto compartido por diez procesos es un secreto que ya no es
 * secreto: cualquiera de los diez podria EMITIR tokens, no solo validarlos. Con RSA,
 * identidad guarda la privada y los demas solo ven la publica por JWKS.
 *
 * <p>En produccion las claves vienen de Vault o de un Secret de Kubernetes. En
 * desarrollo, si no hay ninguna configurada, se genera un par al arrancar: es comodo
 * para levantar rapido y explicitamente inseguro, por eso avisa.
 */
@Configuration
public class ClavesJwtConfig {

    private static final Logger log = LoggerFactory.getLogger(ClavesJwtConfig.class);

    @Value("${pos.jwt.clave-privada:}")
    private String privadaPem;

    @Value("${pos.jwt.clave-publica:}")
    private String publicaPem;

    @Bean
    public RSAKey claveRsa() throws Exception {
        if (privadaPem.isBlank() || publicaPem.isBlank()) {
            log.warn("Sin claves JWT configuradas: se genera un par efimero. "
                    + "Al reiniciar, todos los tokens emitidos dejan de valer. "
                    + "Para produccion, definir pos.jwt.clave-privada y pos.jwt.clave-publica.");
            var generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            KeyPair par = generador.genKeyPair();
            return new RSAKey.Builder((RSAPublicKey) par.getPublic())
                    .privateKey((RSAPrivateKey) par.getPrivate())
                    .keyID("pos-dev")
                    .build();
        }

        var fabrica = KeyFactory.getInstance("RSA");
        var publica = (RSAPublicKey) fabrica.generatePublic(
                new X509EncodedKeySpec(decodificar(publicaPem)));
        var privada = (RSAPrivateKey) fabrica.generatePrivate(
                new PKCS8EncodedKeySpec(decodificar(privadaPem)));

        return new RSAKey.Builder(publica).privateKey(privada).keyID("pos-1").build();
    }

    @Bean
    public JWKSet conjuntoDeClaves(RSAKey clave) {
        // Solo la parte publica sale por el endpoint JWKS. La privada nunca.
        return new JWKSet(clave.toPublicJWK());
    }

    private byte[] decodificar(String pem) {
        String limpio = pem
                .replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "")
                .trim();
        return Base64.getDecoder().decode(limpio);
    }
}

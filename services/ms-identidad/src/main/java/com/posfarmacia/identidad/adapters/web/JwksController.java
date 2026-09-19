package com.posfarmacia.identidad.adapters.web;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publica la clave publica con la que los otros nueve servicios validan los JWT.
 *
 * <p>Este endpoint es la razon por la que identidad no es un punto unico de falla. Los
 * servicios lo consultan una vez, cachean la clave y a partir de ahi validan cada
 * token en memoria, sin una sola llamada de red. Si identidad se cae, las cajas siguen
 * vendiendo con los tokens que ya tienen y lo unico que no se puede hacer es iniciar
 * sesion nueva.
 *
 * <p>La alternativa (preguntarle a identidad por cada request) habria puesto al
 * servicio de login en el camino critico de las 5000 req/s del sistema entero.
 */
@RestController
public class JwksController {

    private final JWKSet claves;

    public JwksController(JWKSet claves) {
        this.claves = claves;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return claves.toJSONObject();
    }
}

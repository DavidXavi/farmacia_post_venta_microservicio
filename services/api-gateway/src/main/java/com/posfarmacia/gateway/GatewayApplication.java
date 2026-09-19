package com.posfarmacia.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Unico puerto expuesto al frontend.
 *
 * <p>Hace tres cosas y ninguna mas: valida la firma del JWT, aplica rate limit y
 * enruta. Cero logica de negocio. Un gateway que empieza a decidir cosas del dominio
 * se convierte en el monolito que se queria evitar, con la diferencia de que ahora
 * esta en el camino de todas las peticiones.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}

package com.posfarmacia.identidad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Usuarios, roles, locales, cajas, login social, MFA TOTP y emision de JWT. Unico emisor de tokens; expone JWKS para que los demas validen sin llamarlo.
 *
 * <p>Arquitectura interna: Clean Architecture por paquetes (identidad.domain,
 * identidad.usecases, identidad.adapters, identidad.infrastructure). La regla de
 * dependencia la impone ArquitecturaTest con ArchUnit, no la buena voluntad del equipo.
 *
 * <p>ponytail: un solo modulo Maven por servicio, no los siete de Clean Architecture
 * replicados nueve veces. 63 pom.xml para probar lo que un test de 20 lineas prueba
 * mejor, mas rapido y sin recompilar el reactor entero.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.posfarmacia.identidad", "com.posfarmacia.plataforma"})
public class IdentidadApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentidadApplication.class, args);
    }
}

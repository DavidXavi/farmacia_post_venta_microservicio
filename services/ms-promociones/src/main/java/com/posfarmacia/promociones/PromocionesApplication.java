package com.posfarmacia.promociones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Reglas de promocion y su evaluacion. CPU pura en el camino mas caliente; se degrada sin bloquear la venta.
 *
 * <p>Arquitectura interna: Clean Architecture por paquetes (promociones.domain,
 * promociones.usecases, promociones.adapters, promociones.infrastructure). La regla de
 * dependencia la impone ArquitecturaTest con ArchUnit, no la buena voluntad del equipo.
 *
 * <p>ponytail: un solo modulo Maven por servicio, no los siete de Clean Architecture
 * replicados nueve veces. 63 pom.xml para probar lo que un test de 20 lineas prueba
 * mejor, mas rapido y sin recompilar el reactor entero.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.posfarmacia.promociones", "com.posfarmacia.plataforma"})
public class PromocionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromocionesApplication.class, args);
    }
}

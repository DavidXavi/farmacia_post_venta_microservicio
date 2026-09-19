package com.posfarmacia.facturacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Comprobantes, notas de credito, devoluciones y envio a SUNAT. Aislado porque SUNAT es lento y se cae, y eso nunca puede parar una caja.
 *
 * <p>Arquitectura interna: Clean Architecture por paquetes (facturacion.domain,
 * facturacion.usecases, facturacion.adapters, facturacion.infrastructure). La regla de
 * dependencia la impone ArquitecturaTest con ArchUnit, no la buena voluntad del equipo.
 *
 * <p>ponytail: un solo modulo Maven por servicio, no los siete de Clean Architecture
 * replicados nueve veces. 63 pom.xml para probar lo que un test de 20 lineas prueba
 * mejor, mas rapido y sin recompilar el reactor entero.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.posfarmacia.facturacion", "com.posfarmacia.plataforma"})
public class FacturacionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FacturacionApplication.class, args);
    }
}

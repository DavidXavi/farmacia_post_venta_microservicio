package com.posfarmacia.credito;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Linea de credito y ledger append-only de movimientos. Es dinero: auditoria y conciliacion propias.
 *
 * <p>Arquitectura interna: Clean Architecture por paquetes (credito.domain,
 * credito.usecases, credito.adapters, credito.infrastructure). La regla de
 * dependencia la impone ArquitecturaTest con ArchUnit, no la buena voluntad del equipo.
 *
 * <p>ponytail: un solo modulo Maven por servicio, no los siete de Clean Architecture
 * replicados nueve veces. 63 pom.xml para probar lo que un test de 20 lineas prueba
 * mejor, mas rapido y sin recompilar el reactor entero.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.posfarmacia.credito", "com.posfarmacia.plataforma"})
public class CreditoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditoApplication.class, args);
    }
}

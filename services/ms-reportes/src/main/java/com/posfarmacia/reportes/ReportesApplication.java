package com.posfarmacia.reportes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Read model CQRS e incentivos. Solo consume eventos: no tiene endpoints de escritura. Saca los escaneos analiticos de la base transaccional.
 *
 * <p>Arquitectura interna: Clean Architecture por paquetes (reportes.domain,
 * reportes.usecases, reportes.adapters, reportes.infrastructure). La regla de
 * dependencia la impone ArquitecturaTest con ArchUnit, no la buena voluntad del equipo.
 *
 * <p>ponytail: un solo modulo Maven por servicio, no los siete de Clean Architecture
 * replicados nueve veces. 63 pom.xml para probar lo que un test de 20 lineas prueba
 * mejor, mas rapido y sin recompilar el reactor entero.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.posfarmacia.reportes", "com.posfarmacia.plataforma"})
public class ReportesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportesApplication.class, args);
    }
}

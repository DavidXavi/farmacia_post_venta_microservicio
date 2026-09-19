package com.posfarmacia.clientes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Clientes, convenios de seguro, coberturas y recetas. Quien compra y bajo que condiciones.
 *
 * <p>Arquitectura interna: Clean Architecture por paquetes (clientes.domain,
 * clientes.usecases, clientes.adapters, clientes.infrastructure). La regla de
 * dependencia la impone ArquitecturaTest con ArchUnit, no la buena voluntad del equipo.
 *
 * <p>ponytail: un solo modulo Maven por servicio, no los siete de Clean Architecture
 * replicados nueve veces. 63 pom.xml para probar lo que un test de 20 lineas prueba
 * mejor, mas rapido y sin recompilar el reactor entero.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.posfarmacia.clientes", "com.posfarmacia.plataforma"})
public class ClientesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientesApplication.class, args);
    }
}

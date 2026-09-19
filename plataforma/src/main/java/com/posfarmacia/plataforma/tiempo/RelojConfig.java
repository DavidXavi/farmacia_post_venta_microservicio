package com.posfarmacia.plataforma.tiempo;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El reloj, uno solo para los nueve servicios.
 *
 * <p>Es un bean y no llamadas sueltas a {@code Instant.now()} porque media docena de
 * reglas dependen de la fecha: el FEFO no despacha un lote que vence manana, una
 * promocion fuera de vigencia no aplica, una receta emitida en el futuro se rechaza.
 * Sin poder fijar el reloj, probar cualquiera de esas significa esperar a manana.
 *
 * <p>Vive en plataforma y no repetido en cada servicio por un fallo concreto: estaba
 * declarado en cuatro de los nueve, y al agregar reglas con fecha a los otros cinco,
 * esos cuatro arrancaron y los cinco restantes murieron con "No qualifying bean of type
 * java.time.Clock". Un bean de infraestructura que hay que acordarse de copiar es un
 * bean que alguien va a olvidar.
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj() {
        return Clock.systemDefaultZone();
    }
}

package com.posfarmacia.identidad.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class IdentidadConfig {

    /**
     * BCrypt con coste 10.
     *
     * <p>El coste es deliberado, no el valor por defecto por casualidad: cada punto
     * duplica el tiempo de verificacion. Con 1500 terminales entrando a las 8 de la
     * manana, un coste de 14 convertiria el login masivo del inicio de turno en una
     * tormenta de CPU. 10 es el equilibrio para este volumen.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}

package com.posfarmacia.identidad.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Los endpoints públicos de ms-identidad.
 *
 * <p>Este servicio es el único con un problema que los otros ocho no tienen: sus
 * endpoints más importantes se usan cuando todavía NO hay token. Pedir autenticación
 * para iniciar sesión es un círculo del que no se sale.
 *
 * <p>Se resuelve con una cadena de filtros propia y de mayor precedencia, acotada por
 * {@code securityMatcher} a esas rutas. La cadena compartida de {@code pos-plataforma}
 * sigue protegiendo todo lo demás (usuarios, roles, locales, cajas), así que la
 * excepción es exactamente del tamaño que tiene que ser.
 *
 * <p>Dos cadenas y no un {@code permitAll} más en la compartida: meter rutas de un
 * servicio concreto en la configuración que comparten los nueve la convierte en el
 * lugar donde todos agregan su excepción, y en un par de meses nadie sabe qué está
 * abierto y por qué.
 */
@Configuration
public class SeguridadIdentidadConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain cadenaPublica(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        // Login con contraseña y verificación del segundo factor: por
                        // definición se llaman sin token.
                        //
                        // Se enumeran una por una y no con /api/auth/**. Con el comodín,
                        // el alta del segundo factor (/api/auth/mfa/estado, /registro)
                        // caía también en esta cadena, quedaba sin autenticar y no había
                        // forma de saber de quién era la cuenta. Abrir de más es más fácil
                        // de no notar que abrir de menos.
                        "/api/auth/login", "/api/auth/refresh", "/api/auth/mfa/verificar",
                        // JWKS: la clave pública con la que los otros nueve validan.
                        // Si esto exigiera token, ningún servicio podría arrancar a validar.
                        "/.well-known/**",
                        // Ida y vuelta del login social.
                        "/oauth2/**", "/login/oauth2/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}

package com.posfarmacia.plataforma.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Validacion de JWT comun a los nueve servicios.
 *
 * <p>Dos decisiones que importan a esta escala:
 *
 * <p><b>Cada servicio valida por su cuenta.</b> El gateway ya valido el token, pero el
 * servicio no confia en eso. Un pod comprometido dentro del cluster no deberia poder
 * saltarse la autorizacion solo por estar del lado de adentro.
 *
 * <p><b>La validacion es local, con la clave publica cacheada del JWKS.</b> Cero
 * llamadas a ms-identidad por request. Eso no es solo rendimiento: si identidad se
 * cae, las cajas siguen vendiendo con los tokens que ya tienen, y lo unico que no se
 * puede hacer es iniciar sesion nueva. Llamar a identidad en cada request convertiria
 * al servicio de login en un punto unico de falla de todo el sistema.
 */
@Configuration
@EnableMethodSecurity
public class SeguridadRecursoConfig {

    /**
     * Cadena por defecto, la de menor precedencia.
     *
     * <p>Va al final a propósito: un servicio que necesite abrir rutas propias (como
     * ms-identidad con el login y el JWKS) declara su cadena con mayor precedencia y
     * acotada por {@code securityMatcher}. Así la excepción vive en el servicio que la
     * necesita y esta configuración compartida no se llena de casos particulares.
     *
     * <p>No lleva {@code @ConditionalOnMissingBean}: con dos clases de configuración de
     * usuario el orden de evaluación no está garantizado, y el resultado sería que a
     * veces queda todo abierto. El orden explícito sí es determinista.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain cadena(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Actuator queda abierto solo dentro del cluster: en Kubernetes el
                        // puerto de management no se publica en el Service de trafico.
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor())));
        return http.build();
    }

    /** Toma roles y permisos del token y los convierte en authorities de Spring. */
    private JwtAuthenticationConverter convertidor() {
        var scopes = new JwtGrantedAuthoritiesConverter();
        var conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(jwt -> unir(scopes.convert(jwt), rolesDe(jwt)));
        return conv;
    }

    private Collection<GrantedAuthority> unir(Collection<GrantedAuthority> a, Collection<GrantedAuthority> b) {
        return Stream.concat(a == null ? Stream.of() : a.stream(), b.stream()).toList();
    }

    private Collection<GrantedAuthority> rolesDe(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    }
}

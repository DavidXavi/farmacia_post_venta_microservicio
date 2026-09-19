package com.posfarmacia.gateway;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

/**
 * Seguridad y limites del gateway.
 *
 * <p>El gateway valida la firma del JWT y corta lo que no la trae. Los servicios de
 * atras vuelven a validarlo por su cuenta: no confian en que el gateway ya lo hizo.
 * Un pod comprometido dentro del cluster no deberia poder saltarse la autorizacion
 * solo por estar del lado de adentro.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewayConfig {

    @Value("${pos.cors.origenes}")
    private String origenesPermitidos;

    @Bean
    public SecurityWebFilterChain cadena(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfig()))
                .authorizeExchange(ex -> ex
                        // Login, segundo factor, refresh y JWKS son publicos por
                        // definicion: es lo que se usa cuando todavia no hay token.
                        //
                        // mfa/verificar estaba fuera de la lista y el efecto era que
                        // activar el segundo factor dejaba al usuario fuera del sistema:
                        // el login devolvia un token pendiente, y el unico endpoint que
                        // lo convierte en token real exigia un token real. El endpoint
                        // valida el token pendiente por su cuenta, asi que abrirlo aqui
                        // no abre nada que no estuviera ya protegido.
                        .pathMatchers("/api/auth/login", "/api/auth/refresh",
                                "/api/auth/mfa/verificar",
                                "/api/auth/oauth2/**", "/login/oauth2/**",
                                "/.well-known/jwks.json").permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> {
                }))
                .build();
    }

    /**
     * Clave del rate limit: el usuario autenticado, y si no hay, la IP.
     *
     * <p>Limitar por IP sola no sirve: los 3 POS de una botica salen por la misma IP
     * publica, asi que uno en bucle de reintento castigaria a los otros dos.
     */
    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.KeyResolver resolverPorUsuario() {
        return intercambio -> intercambio.getPrincipal()
                .map(p -> p instanceof JwtAuthenticationToken jwt
                        ? jwt.getToken().getSubject()
                        : p.getName())
                .defaultIfEmpty(ipDe(intercambio));
    }

    private String ipDe(org.springframework.web.server.ServerWebExchange intercambio) {
        var remota = intercambio.getRequest().getRemoteAddress();
        return remota == null ? "desconocida" : remota.getAddress().getHostAddress();
    }

    private CorsConfigurationSource corsConfig() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origenesPermitidos.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        var fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }
}

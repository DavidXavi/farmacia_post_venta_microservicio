package com.posfarmacia.plataforma.idempotencia;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Idempotencia de las APIs que mutan estado.
 *
 * <p>Un POS reintenta. Una caja con wifi de botica reintenta seguido. Un reintento
 * que cobra dos veces no es un problema tecnico, es un problema legal con el cliente
 * parado en el mostrador.
 *
 * <p>Todo POST, PUT y PATCH exige el header {@code Idempotency-Key} (un UUID que
 * genera el POS). La respuesta se guarda en Redis por 24 h; un reintento con la misma
 * clave devuelve la respuesta original sin volver a ejecutar nada.
 *
 * <p>La clave va en Redis y no en Postgres a proposito: son efimeras, de altisima
 * frecuencia y no hace falta que sobrevivan a nada. Una tabla para esto seria
 * escritura transaccional gratis en el camino mas caliente del sistema.
 */
@Component
public class FiltroIdempotencyKey extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FiltroIdempotencyKey.class);

    private static final String HEADER = "Idempotency-Key";
    private static final Set<String> METODOS_MUTANTES = Set.of("POST", "PUT", "PATCH");
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public FiltroIdempotencyKey(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METODOS_MUTANTES.contains(request.getMethod())
                || request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String clave = req.getHeader(HEADER);
        if (clave == null || clave.isBlank()) {
            // No se rechaza: hay endpoints administrativos donde el reintento no hace dano.
            // Los criticos (confirmar venta, registrar pago) si lo exigen, y lo validan ellos.
            chain.doFilter(req, res);
            return;
        }

        String llaveRedis = "idem:" + req.getRequestURI() + ":" + clave;
        String guardada = redis.opsForValue().get(llaveRedis);
        if (guardada != null) {
            log.info("Reintento idempotente de {} con clave {}: se devuelve la respuesta original",
                    req.getRequestURI(), clave);
            res.setStatus(200);
            res.setContentType("application/json");
            res.getWriter().write(guardada);
            return;
        }

        var envoltura = new ContentCachingResponseWrapper(res);
        chain.doFilter(req, envoltura);

        // Solo se cachea lo que salio bien. Un 500 debe poder reintentarse de verdad:
        // guardar el error convertiria una falla transitoria en permanente.
        if (envoltura.getStatus() >= 200 && envoltura.getStatus() < 300) {
            String cuerpo = new String(envoltura.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            redis.opsForValue().set(llaveRedis, cuerpo, TTL);
        }
        envoltura.copyBodyToResponse();
    }

    @SuppressWarnings("unused")
    private static String leer(HttpServletRequest req) throws IOException {
        return StreamUtils.copyToString(req.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
    }
}

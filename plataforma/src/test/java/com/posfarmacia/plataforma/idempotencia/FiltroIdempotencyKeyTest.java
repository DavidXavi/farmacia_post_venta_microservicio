package com.posfarmacia.plataforma.idempotencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FiltroIdempotencyKeyTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final FiltroIdempotencyKey filtro = new FiltroIdempotencyKey(redis);
    private final AtomicInteger ejecuciones = new AtomicInteger();

    private final FilterChain cadena = (req, res) -> {
        ejecuciones.incrementAndGet();
        ((HttpServletResponse) res).setStatus(201);
        res.getWriter().write("{\"estado\":\"CONFIRMADA\"}");
    };

    private MockHttpServletResponse confirmar() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/ventas/1/confirmar");
        req.addHeader("Idempotency-Key", "clave-1");
        var res = new MockHttpServletResponse();
        filtro.doFilter(req, res, cadena);
        return res;
    }

    @Test
    void conRedisCaidoLaVentaSeConfirmaIgual() throws Exception {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("redis caido"));

        var res = confirmar();

        assertThat(res.getStatus()).isEqualTo(201);
        assertThat(res.getContentAsString()).contains("CONFIRMADA");
        assertThat(ejecuciones).hasValue(1);
    }

    @Test
    void siRedisFallaAlGuardarLaRespuestaIgualLlega() throws Exception {
        when(redis.opsForValue()).thenReturn(valores);
        doThrow(new RedisConnectionFailureException("redis caido"))
                .when(valores).set(anyString(), anyString(), any(Duration.class));

        var res = confirmar();

        assertThat(res.getStatus()).isEqualTo(201);
        assertThat(res.getContentAsString()).contains("CONFIRMADA");
    }

    @Test
    void conRedisVivoElReintentoDevuelveLaRespuestaGuardadaSinEjecutar() throws Exception {
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.get("idem:/api/ventas/1/confirmar:clave-1")).thenReturn("{\"estado\":\"CONFIRMADA\"}");

        var res = confirmar();

        assertThat(ejecuciones).hasValue(0);
        assertThat(res.getContentAsString()).contains("CONFIRMADA");
    }

    @Test
    void conRedisVivoLaPrimeraVezSeGuardaLaRespuesta() throws Exception {
        when(redis.opsForValue()).thenReturn(valores);

        confirmar();

        assertThat(ejecuciones).hasValue(1);
        verify(valores).set(eq("idem:/api/ventas/1/confirmar:clave-1"), anyString(), eq(Duration.ofHours(24)));
    }
}

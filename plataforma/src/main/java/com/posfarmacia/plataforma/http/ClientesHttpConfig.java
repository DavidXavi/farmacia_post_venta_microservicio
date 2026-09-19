package com.posfarmacia.plataforma.http;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP entre servicios, con los limites puestos.
 *
 * <p>El timeout no es opcional a esta escala. Una llamada sin timeout que se queda
 * colgada ocupa un hilo del pool; suficientes de esas y el servicio deja de atender
 * lo que si podria atender. Asi es como la lentitud de un servicio secundario termina
 * parando las cajas: no porque el servicio critico falle, sino porque se quedo sin
 * hilos esperando al que no importaba.
 *
 * <p>Los tiempos por destino no se deciden aca sino donde se construye cada cliente,
 * porque cada dependencia tiene su presupuesto de latencia propio: inventario 500 ms,
 * credito 800 ms, catalogo 200 ms, promociones 300 ms, clientes 300 ms.
 */
@Configuration
public class ClientesHttpConfig {

    /**
     * Construye un cliente hacia otro microservicio.
     *
     * <p>Hace dos cosas que ningun cliente entre servicios deberia dejar de hacer:
     * poner un timeout propio y propagar el JWT del cajero, para que la autorizacion
     * no se pierda en el salto y el servicio de atras pueda decidir por si mismo.
     *
     * @param baseUrl        raiz del destino, por ejemplo http://ms-inventario:8080
     * @param timeoutLectura presupuesto de latencia de esta dependencia en concreto
     */
    public static RestClient cliente(String baseUrl, Duration timeoutLectura) {
        var fabrica = new JdkClientHttpRequestFactory();
        fabrica.setReadTimeout(timeoutLectura);

        // Envuelto en BufferingClientHttpRequestFactory por una razon concreta:
        // Jackson 3 lee mas alla del final del JSON para verificar que no haya tokens
        // sobrantes, y para entonces el stream del HttpClient de la JDK ya esta cerrado.
        // El sintoma es un "java.io.IOException: closed" DESPUES de haber recibido la
        // respuesta completa, lo cual despista mucho: parece un fallo de red y es de
        // parseo. Buffereando, el cuerpo se lee entero antes de parsear. Las respuestas
        // entre servicios aqui son pequenas, asi que no cuesta nada.
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new BufferingClientHttpRequestFactory(fabrica))
                .requestInterceptor((peticion, cuerpo, ejecucion) -> {
                    var auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth instanceof JwtAuthenticationToken jwt) {
                        peticion.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
                    }
                    return ejecucion.execute(peticion, cuerpo);
                })
                .build();
    }

    /**
     * Circuit breaker por defecto para toda llamada saliente.
     *
     * <p>Cuando un destino acumula fallas, el circuito se abre y deja de intentarlo por
     * 10 s. Eso protege dos veces: al servicio caido, que deja de recibir trafico
     * mientras se levanta, y al que llama, que deja de gastar su presupuesto de
     * latencia esperando una respuesta que ya sabe que no va a llegar.
     *
     * <p>Cuando un circuito abre se publica como metrica y el tablero lo muestra por
     * local. Degradacion silenciosa es peor que caida ruidosa: si promociones lleva
     * dos horas sin aplicar descuentos, alguien tiene que enterarse antes que el cliente.
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> circuitosPorDefecto() {
        return fabrica -> fabrica.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build())
                // Red de seguridad: aunque falle el timeout del cliente HTTP, la llamada
                // no se queda colgada mas de 2 s.
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(2))
                        .build())
                .build());
    }
}

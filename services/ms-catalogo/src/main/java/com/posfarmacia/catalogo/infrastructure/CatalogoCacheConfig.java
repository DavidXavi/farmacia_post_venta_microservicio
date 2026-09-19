package com.posfarmacia.catalogo.infrastructure;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Cache de dos niveles.
 *
 * <p>Nivel 1, Caffeine en memoria del pod, TTL 60 s: atiende el 99% de las lecturas
 * sin salir del proceso. Nivel 2, Redis, TTL 10 min, compartido entre replicas: cubre
 * el arranque de un pod nuevo y los productos menos frecuentes.
 *
 * <p>Los TTL son cortos a proposito. Con invalidacion por evento como unico mecanismo,
 * un evento perdido dejaria un precio viejo circulando indefinidamente. Con TTL de 60 s,
 * el peor caso de un evento perdido es un minuto de precio desactualizado y el sistema
 * se corrige solo. Esa combinacion (evento para lo rapido, TTL para lo seguro) es lo
 * que evita tener que hacer la invalidacion transaccional y perfecta, que seria cara
 * y fragil.
 */
@Configuration
@EnableCaching
public class CatalogoCacheConfig {

    @Bean
    @Primary
    public CacheManager cacheLocal() {
        var gestor = new CaffeineCacheManager("productos", "productos-barras", "categorias");
        gestor.setCaffeine(Caffeine.newBuilder()
                .maximumSize(20_000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .recordStats());
        return gestor;
    }
}

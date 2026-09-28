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
 * Cache de catalogo en memoria del pod: Caffeine, TTL 60 s.
 *
 * <p>Atiende el 99% de las lecturas sin salir del proceso: son los mismos doscientos
 * productos y caben de sobra en la memoria de cada replica.
 *
 * <p>El TTL es corto a proposito. El pod que cambia un producto limpia su cache al
 * instante; los demas se corrigen solos en 60 s como maximo. Un minuto de precio
 * desactualizado en el peor caso, sin invalidacion distribuida que mantener.
 *
 * <p>ponytail: un solo nivel. Redis como segundo nivel solo ayudaria al pod recien
 * arrancado, que tarda unos segundos en llenar su cache contra Postgres. Entra si las
 * pruebas de carga muestran que ese arranque en frio pega en el p99.
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

package com.posfarmacia.catalogo.usecases.usecase;

import com.posfarmacia.catalogo.usecases.port.out.ProductoPort;
import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.api.ProductoDto;
import com.posfarmacia.contracts.eventos.CatalogoCambiado;
import com.posfarmacia.plataforma.outbox.OutboxRegistrador;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de catalogo. Es el servicio mas leido del sistema: 5000 req/s proyectados
 * contra datos que cambian una vez a la semana.
 *
 * <p>Por eso la cache no es un adorno. Es el unico cambio de esta arquitectura que
 * mueve el orden de magnitud: una lectura que no sale del proceso cuesta microsegundos
 * contra los milisegundos de una consulta a Postgres, incluso indexada. Todo lo demas
 * (mas replicas, mas conexiones, particionar) da mejoras lineales; la cache da 10x a 100x.
 */
@Service
public class ConsultarCatalogoUseCase {

    private final ProductoPort productos;
    private final OutboxRegistrador outbox;

    public ConsultarCatalogoUseCase(ProductoPort productos, OutboxRegistrador outbox) {
        this.productos = productos;
        this.outbox = outbox;
    }

    /**
     * Cache en memoria del pod, TTL 60 s (ver CatalogoCacheConfig). Sin salto de red:
     * un viaje a Redis por cada producto escaneado serian 5000 round trips por segundo
     * que no hacen falta.
     */
    @Cacheable(cacheNames = "productos", key = "#id", unless = "#result == null")
    public ProductoDto porId(UUID id) {
        return productos.porId(id).orElse(null);
    }

    public List<ProductoDto> porIds(List<UUID> ids) {
        return productos.porIds(ids);
    }

    @Cacheable(cacheNames = "productos-barras", key = "#codigo", unless = "#result == null")
    public ProductoDto porCodigoBarras(String codigo) {
        return productos.porCodigoBarras(codigo).orElse(null);
    }

    public List<ProductoDto> buscar(String texto, int limite) {
        return productos.buscarPorNombre(texto, limite);
    }

    /** Listado completo para la pantalla de administracion. No se cachea: cambia poco pero se pide poco. */
    public List<ProductoDto> listar(int limite) {
        return productos.listar(limite);
    }

    /**
     * Cambiar un precio invalida la cache local Y publica el evento para que la tumben
     * los otros nueve servicios y las otras cinco replicas de este.
     *
     * <p>Con TTL corto, un evento perdido se corrige solo en un minuto. Por eso la
     * invalidacion puede ser asincrona sin causar un problema real: el peor caso es
     * que una caja cobre 60 s con el precio anterior.
     */
    @Transactional
    @CacheEvict(cacheNames = {"productos", "productos-barras"}, allEntries = true)
    public void cambiarPrecio(UUID productoId, BigDecimal nuevoPrecio) {
        productos.actualizarPrecio(productoId, nuevoPrecio);
        outbox.registrar("producto", productoId, Topicos.CATALOGO_CAMBIOS, productoId,
                new CatalogoCambiado(productoId, "CAMBIO_PRECIO", Instant.now()));
    }
}

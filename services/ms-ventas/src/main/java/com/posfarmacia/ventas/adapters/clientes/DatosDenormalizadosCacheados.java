package com.posfarmacia.ventas.adapters.clientes;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.posfarmacia.ventas.usecases.port.out.ServiciosExternosPort;
import com.posfarmacia.ventas.usecases.usecase.DatosDenormalizados;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resuelve los nombres que el evento {@code VentaConfirmada} lleva adentro aunque
 * ms-ventas no sea dueño de ellos.
 *
 * <p>Son datos que cambian una vez al año (nombre de un local, de un vendedor, de una
 * categoría) y se consultan en cada venta. Cachearlos diez minutos en memoria del pod
 * convierte lo que serían 200 llamadas por segundo a otros servicios en prácticamente cero.
 *
 * <p><b>El nombre del vendedor sale del JWT.</b> El token ya trae {@code nombre_usuario}:
 * llamar a ms-identidad para un dato que viene firmado en la credencial sería pagar un
 * salto de red por nada, y encima metería a identidad en el camino de cada venta.
 *
 * <p>Lo que no se pueda resolver devuelve null, y el read model lo acepta. Un nombre
 * para mostrar no puede impedir que una venta aparezca en el tablero, y mucho menos
 * que se cobre.
 */
@Component
public class DatosDenormalizadosCacheados implements DatosDenormalizados {

    private static final Logger log = LoggerFactory.getLogger(DatosDenormalizadosCacheados.class);

    private final Cache<UUID, String> nombresUsuario = Caffeine.newBuilder()
            .maximumSize(5_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    private final Cache<UUID, Categoria> categorias = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    private final Cache<UUID, String> nombresCliente = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    // 500 locales: caben todos. Un nombre de local cambia una vez al anio.
    private final Cache<UUID, String> nombresLocal = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    private final ServiciosExternosPort servicios;

    public DatosDenormalizadosCacheados(ServiciosExternosPort servicios) {
        this.servicios = servicios;
    }

    /** Lo recuerda quien atendió: el cajero identifica al cliente y aquí se guarda. */
    @Override
    public void recordarCliente(UUID clienteId, String nombre) {
        if (clienteId != null && nombre != null) {
            nombresCliente.put(clienteId, nombre);
        }
    }

    /** Idem para la categoría: se aprende del producto que ya se consultó al escanear. */
    @Override
    public void recordarCategoria(UUID productoId, UUID categoriaId, String categoriaNombre) {
        if (productoId != null && categoriaId != null) {
            categorias.put(productoId, new Categoria(categoriaId, categoriaNombre));
        }
    }

    @Override
    public String nombreLocal(UUID localId) {
        if (localId == null) {
            return null;
        }
        String cacheado = nombresLocal.getIfPresent(localId);
        if (cacheado != null) {
            return cacheado;
        }
        // Un null (identidad caida) no se cachea: la venta siguiente lo vuelve a intentar.
        String nombre = servicios.nombreLocal(localId);
        if (nombre != null) {
            nombresLocal.put(localId, nombre);
        }
        return nombre;
    }

    @Override
    public String nombreUsuario(UUID usuarioId) {
        if (usuarioId == null) {
            return null;
        }
        String delToken = deJwt();
        if (delToken != null) {
            nombresUsuario.put(usuarioId, delToken);
            return delToken;
        }
        return nombresUsuario.getIfPresent(usuarioId);
    }

    @Override
    public String nombreCliente(UUID clienteId) {
        return clienteId == null ? null : nombresCliente.getIfPresent(clienteId);
    }

    @Override
    public Categoria categoriaDe(UUID productoId) {
        return productoId == null ? null : categorias.getIfPresent(productoId);
    }

    /** El nombre del cajero viene firmado en el token: cero llamadas de red. */
    private String deJwt() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt) {
                return jwt.getToken().getClaimAsString("nombre_usuario");
            }
        } catch (RuntimeException e) {
            log.debug("No se pudo leer el nombre del usuario del token: {}", e.getMessage());
        }
        return null;
    }
}

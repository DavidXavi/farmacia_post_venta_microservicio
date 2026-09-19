package com.posfarmacia.facturacion.adapters.clientes;

import com.posfarmacia.facturacion.usecases.port.out.VentaConsultadaPort;
import com.posfarmacia.plataforma.http.ClientesHttpConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Consulta a ms-ventas para armar una devolucion.
 *
 * <p>Presupuesto de 1 s, mas holgado que los del camino de venta: aqui no hay un
 * cliente esperando en la caja, hay un empleado tramitando una devolucion. Si ms-ventas
 * no responde, la devolucion falla con un motivo legible en vez de calcularse con datos
 * inventados.
 */
@Component
public class VentasDeServicio implements VentaConsultadaPort {

    private static final Logger log = LoggerFactory.getLogger(VentasDeServicio.class);

    private final RestClient ventas;
    private final CircuitBreakerFactory<?, ?> circuitos;

    public VentasDeServicio(
            @Value("${pos.uri.ventas:http://ms-ventas:8080}") String uriVentas,
            CircuitBreakerFactory<?, ?> circuitos) {
        this.ventas = ClientesHttpConfig.cliente(uriVentas, Duration.ofMillis(1000));
        this.circuitos = circuitos;
    }

    /** Lo que ms-ventas devuelve en GET /api/ventas/{id}. Solo los campos que hacen falta. */
    private record VentaVista(UUID id, String estado, List<DetalleVista> detalles) {
    }

    private record DetalleVista(UUID id, UUID productoId, int cantidad, BigDecimal subtotal) {
    }

    @Override
    public VentaVendida porId(UUID ventaId) {
        VentaVista vista = circuitos.create("ventas").run(
                () -> ventas.get().uri("/api/ventas/{id}", ventaId)
                        .retrieve()
                        .body(VentaVista.class),
                fallo -> {
                    log.error("Ventas no respondio por la venta {}: {}", ventaId, fallo.getMessage());
                    throw new IllegalStateException(
                            "No se pudo consultar la venta para calcular la devolucion. "
                                    + "Reintentar en unos segundos.", fallo);
                });

        if (vista == null || vista.detalles() == null) {
            throw new IllegalArgumentException("No existe la venta " + ventaId);
        }

        return new VentaVendida(vista.id(), null, vista.estado(),
                vista.detalles().stream()
                        .map(d -> new LineaVendida(d.id(), d.productoId(), d.cantidad(),
                                d.subtotal()))
                        .toList());
    }
}

package com.posfarmacia.ventas.adapters.clientes;

import com.posfarmacia.contracts.api.CargoCreditoRespuesta;
import com.posfarmacia.contracts.api.CargoCreditoSolicitud;
import com.posfarmacia.contracts.api.ClienteDto;
import com.posfarmacia.contracts.api.CoberturaDto;
import com.posfarmacia.contracts.api.EvaluarPromocionesRespuesta;
import com.posfarmacia.contracts.api.EvaluarPromocionesSolicitud;
import com.posfarmacia.contracts.api.ProductoDto;
import com.posfarmacia.contracts.api.ReservaRespuesta;
import com.posfarmacia.contracts.api.ReservaSolicitud;
import com.posfarmacia.ventas.usecases.port.out.ServiciosExternosPort;
import com.posfarmacia.plataforma.http.ClientesHttpConfig;
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
 * Las cinco dependencias sincronas de ms-ventas, cada una con su presupuesto de
 * latencia y su comportamiento cuando falla.
 *
 * <p>La regla que ordena todo este archivo: <b>solo stock y credito pueden impedir
 * una venta.</b> Todo lo demas se degrada. Un sistema de punto de venta que se niega
 * a cobrar porque el servicio de promociones esta lento no es un sistema resiliente,
 * es un sistema que le hizo perder la venta a la botica.
 *
 * <table>
 *   <tr><th>Destino</th><th>Timeout</th><th>Si falla</th></tr>
 *   <tr><td>inventario</td><td>500 ms</td><td>falla la linea, el cajero lo ve</td></tr>
 *   <tr><td>credito</td><td>800 ms</td><td>falla el pago a credito, se ofrece efectivo</td></tr>
 *   <tr><td>catalogo</td><td>200 ms</td><td>sirve del cache aunque este vencido</td></tr>
 *   <tr><td>promociones</td><td>300 ms</td><td>venta sin promocion</td></tr>
 *   <tr><td>clientes</td><td>300 ms</td><td>venta anonima</td></tr>
 * </table>
 */
@Component
public class ClientesDeServicios implements ServiciosExternosPort {

    private static final Logger log = LoggerFactory.getLogger(ClientesDeServicios.class);

    private final RestClient catalogo;
    private final RestClient inventario;
    private final RestClient promociones;
    private final RestClient clientes;
    private final RestClient credito;
    private final CircuitBreakerFactory<?, ?> circuitos;

    public ClientesDeServicios(
            @Value("${pos.uri.catalogo}") String uriCatalogo,
            @Value("${pos.uri.inventario}") String uriInventario,
            @Value("${pos.uri.promociones}") String uriPromociones,
            @Value("${pos.uri.clientes}") String uriClientes,
            @Value("${pos.uri.credito}") String uriCredito,
            CircuitBreakerFactory<?, ?> circuitos) {
        this.catalogo = ClientesHttpConfig.cliente(uriCatalogo, Duration.ofMillis(200));
        this.inventario = ClientesHttpConfig.cliente(uriInventario, Duration.ofMillis(500));
        this.promociones = ClientesHttpConfig.cliente(uriPromociones, Duration.ofMillis(300));
        this.clientes = ClientesHttpConfig.cliente(uriClientes, Duration.ofMillis(300));
        this.credito = ClientesHttpConfig.cliente(uriCredito, Duration.ofMillis(800));
        this.circuitos = circuitos;
    }

    // ------------------------------------------------------------------
    // CRITICAS: si fallan, la venta no puede continuar
    // ------------------------------------------------------------------

    /**
     * Apartar stock. Sin respuesta afirmativa no hay linea de venta.
     *
     * <p>El fallback no deja pasar la venta: vender sin saber si hay stock es
     * prometerle al cliente algo que puede no estar en el anaquel, y eso cuesta mas
     * que perder la venta. Lo que si hace es fallar con un motivo legible.
     *
     * <p>Sin fallback, resilience4j lanza NoFallbackAvailableException y el cajero ve
     * "error interno" con el cliente en el mostrador. "Comportamiento definido cuando
     * falla" incluye decir por que.
     */
    @Override
    public ReservaRespuesta reservarStock(ReservaSolicitud solicitud) {
        return circuitos.create("inventario").run(
                () -> inventario.post().uri("/api/reservas")
                        .body(solicitud)
                        .retrieve()
                        .body(ReservaRespuesta.class),
                fallo -> {
                    throw new IllegalStateException(
                            "Inventario no responde: no se puede apartar stock. "
                                    + "Reintentar en unos segundos.", fallo);
                });
    }

    /** Reservar credito. Si falla, el cajero ofrece otra forma de pago. */
    @Override
    public CargoCreditoRespuesta reservarCredito(CargoCreditoSolicitud solicitud) {
        return circuitos.create("credito").run(
                () -> credito.post().uri("/api/creditos/reservas")
                        .body(solicitud)
                        .retrieve()
                        .body(CargoCreditoRespuesta.class),
                fallo -> {
                    throw new IllegalStateException(
                            "Credito no responde: cobrar con otra forma de pago.", fallo);
                });
    }

    // ------------------------------------------------------------------
    // DEGRADABLES: si fallan, la venta sigue con menos informacion
    // ------------------------------------------------------------------

    /**
     * Producto por id. En lote, nunca de a uno.
     *
     * <p>Una venta de cinco lineas consultada de a una son cinco llamadas; con 200
     * ventas/s eso son 1000 req/s extra contra catalogo solo por no haber pedido las
     * cinco juntas. El fan-out es lo que mata bajo carga, no la latencia de cada llamada.
     */
    @Override
    public List<ProductoDto> productos(List<UUID> ids) {
        return circuitos.create("catalogo").run(
                () -> catalogo.get()
                        .uri(uri -> uri.path("/api/productos").queryParam("ids", ids).build())
                        .retrieve()
                        .body(new org.springframework.core.ParameterizedTypeReference<List<ProductoDto>>() {
                        }),
                fallo -> {
                    // Sin catalogo no se puede poner precio: esta si es critica.
                    // El fallback existe solo para dar un error claro en vez de un timeout.
                    log.error("Catalogo no responde: {}", fallo.getMessage());
                    throw new IllegalStateException("El catalogo no responde, no se puede cotizar", fallo);
                });
    }

    /** Promociones. Si no contesta en 300 ms, la venta entra sin descuento. */
    @Override
    public EvaluarPromocionesRespuesta evaluarPromociones(EvaluarPromocionesSolicitud solicitud) {
        return circuitos.create("promociones").run(
                () -> promociones.post().uri("/api/promociones/evaluar")
                        .body(solicitud)
                        .retrieve()
                        .body(EvaluarPromocionesRespuesta.class),
                fallo -> {
                    // Degradacion explicita y registrada. Silenciosa seria peor: si
                    // promociones lleva dos horas caida, alguien tiene que enterarse
                    // antes que el cliente que no le aplicaron su descuento.
                    log.warn("Promociones no respondio, la venta {} sigue sin descuentos: {}",
                            solicitud.ventaId(), fallo.getMessage());
                    return EvaluarPromocionesRespuesta.vacia();
                });
    }

    /** Coberturas del convenio para varios productos. En lote, nunca de a uno. */
    @Override
    public List<CoberturaDto> coberturas(UUID convenioId, List<UUID> productoIds) {
        return circuitos.create("clientes").run(
                () -> clientes.get()
                        .uri(uri -> uri.path("/api/clientes/coberturas")
                                .queryParam("convenioId", convenioId)
                                .queryParam("productoIds", productoIds).build())
                        .retrieve()
                        .body(new org.springframework.core.ParameterizedTypeReference<List<CoberturaDto>>() {
                        }),
                fallo -> {
                    // Sin cobertura se cobra el total: el cliente reclama despues, que
                    // es preferible a no poder cobrar.
                    log.warn("Clientes no respondio por coberturas del convenio {}: {}",
                            convenioId, fallo.getMessage());
                    return List.of();
                });
    }

    /** Cliente por documento. Si no responde, la venta sigue como anonima. */
    @Override
    public ClienteDto clientePorDni(String dni) {
        return circuitos.create("clientes").run(
                () -> clientes.get().uri("/api/clientes/por-dni/{dni}", dni)
                        .retrieve()
                        .body(ClienteDto.class),
                fallo -> {
                    log.warn("Clientes no respondio para el DNI {}, se vende como anonimo", dni);
                    return null;
                });
    }
}

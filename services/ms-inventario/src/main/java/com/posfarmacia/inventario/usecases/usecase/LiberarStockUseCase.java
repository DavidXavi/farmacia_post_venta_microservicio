package com.posfarmacia.inventario.usecases.usecase;

import com.posfarmacia.contracts.eventos.VentaAnulada;
import com.posfarmacia.inventario.domain.EstadoReserva;
import com.posfarmacia.inventario.usecases.port.out.LotePort;
import com.posfarmacia.inventario.usecases.port.out.ReservaPort;
import com.posfarmacia.inventario.usecases.port.out.StockPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La compensacion. Es la mitad menos vistosa de la saga y la que decide si el sistema
 * es correcto: cualquiera sabe apartar stock, lo dificil es devolverlo siempre.
 *
 * <p>Dos caminos llegan aca:
 * <ul>
 *   <li>La venta se anulo o el pago fallo: llega el evento {@code VentaAnulada}.</li>
 *   <li>La caja se colgo a mitad de venta y nadie anulo nada: el TTL de la reserva
 *       vence y el job de limpieza la libera. Sin este segundo camino, el stock
 *       apartado por una venta fantasma queda bloqueado para siempre y el producto
 *       aparece agotado teniendolo en el anaquel.</li>
 * </ul>
 */
@Service
public class LiberarStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(LiberarStockUseCase.class);

    private final StockPort stock;
    private final ReservaPort reservas;
    private final LotePort lotes;
    private final Counter compensaciones;
    private final Counter vencidasLiberadas;

    public LiberarStockUseCase(StockPort stock, ReservaPort reservas, LotePort lotes,
            MeterRegistry metricas) {
        this.stock = stock;
        this.reservas = reservas;
        this.lotes = lotes;
        this.compensaciones = Counter.builder("saga.venta.compensada.total")
                .description("Ventas cuyo stock hubo que devolver")
                .register(metricas);
        this.vencidasLiberadas = Counter.builder("reservas.vencidas.liberadas.total")
                .description("Reservas liberadas por vencimiento del TTL")
                .register(metricas);
    }

    /** Compensacion por anulacion de venta. */
    @Transactional
    public void compensar(VentaAnulada venta) {
        var activas = reservas.activasDeVenta(venta.ventaId());

        if (!activas.isEmpty()) {
            // La venta se anulo antes de que el consumidor confirmara: basta soltar
            // lo apartado, los lotes nunca se tocaron.
            activas.forEach(r -> {
                stock.liberar(r.productoId(), r.localId(), r.cantidad());
                reservas.cambiarEstado(r.id(), EstadoReserva.LIBERADA);
            });
        } else {
            // La salida ya se habia aplicado: hay que devolver a los lotes de los que
            // salio, no a cualquiera, o se rompe la trazabilidad del vencimiento.
            venta.lineas().forEach(l -> {
                lotes.revertirSalida(venta.ventaId(), l.productoId(),
                        venta.localId(), venta.usuarioId());
                // devolver, no liberar: el reservado ya bajo cuando se confirmo la
                // salida, asi que lo que falta reponer es el disponible. Con liberar,
                // los lotes recuperaban las unidades y el contador no, y stock_local
                // quedaba por debajo de lo que hay en el anaquel.
                stock.devolver(l.productoId(), venta.localId(), l.cantidad());
            });
        }

        compensaciones.increment();
        log.warn("Compensado el stock de la venta {} por: {}", venta.ventaId(), venta.motivo());
    }

    /**
     * Libera reservas cuyo TTL ya paso.
     *
     * <p>ponytail: un job cada 30 s con lotes de 500, no un scheduler distribuido.
     * Varias replicas pueden correrlo a la vez sin pisarse porque cada liberacion es
     * idempotente por estado (solo pasa de ACTIVA a VENCIDA una vez). El techo: si
     * hubiera millones de reservas vencidas, 500 por ciclo no alcanzaria y ahi si
     * tocaria particionar el barrido por local.
     */
    @Scheduled(fixedDelayString = "${pos.reserva.limpieza-ms:30000}")
    @Transactional
    public void liberarVencidas() {
        var vencidas = reservas.vencidas(500);
        if (vencidas.isEmpty()) {
            return;
        }
        vencidas.forEach(r -> {
            stock.liberar(r.productoId(), r.localId(), r.cantidad());
            reservas.cambiarEstado(r.id(), EstadoReserva.VENCIDA);
            vencidasLiberadas.increment();
        });
        log.info("Liberadas {} reservas vencidas: el stock vuelve solo", vencidas.size());
    }
}

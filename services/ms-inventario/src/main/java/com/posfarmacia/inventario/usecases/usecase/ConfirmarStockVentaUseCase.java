package com.posfarmacia.inventario.usecases.usecase;

import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.LotesAsignados;
import com.posfarmacia.contracts.eventos.VentaConfirmada;
import com.posfarmacia.inventario.domain.AsignacionLote;
import com.posfarmacia.inventario.domain.AsignadorFefo;
import com.posfarmacia.inventario.domain.EstadoReserva;
import com.posfarmacia.inventario.usecases.port.out.EventoPort;
import com.posfarmacia.inventario.usecases.port.out.LotePort;
import com.posfarmacia.inventario.usecases.port.out.ReservaPort;
import com.posfarmacia.inventario.usecases.port.out.StockPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierra la parte de inventario de la saga: la venta ya se confirmo, ahora el stock
 * apartado se convierte en salida definitiva y se decide de que lotes sale.
 *
 * <p>Corre en el consumidor de {@code pos.ventas.confirmadas}, fuera del camino
 * critico. El cajero ya se fue con su cliente cuando esto pasa, y eso es exactamente
 * el diseno: la escritura cara (descontar lotes, registrar movimientos, calcular
 * FEFO) no le cuesta latencia a la caja.
 *
 * <p>Esta es la ventana de inconsistencia de la que habla el diseno: entre que la
 * venta se confirma y este metodo corre pasan segundos. Durante esos segundos el
 * lote todavia figura completo. Es el costo real de partir el monolito, no un
 * defecto de la implementacion, y se dice explicito en la sustentacion.
 */
@Service
public class ConfirmarStockVentaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmarStockVentaUseCase.class);

    private final StockPort stock;
    private final ReservaPort reservas;
    private final LotePort lotes;
    private final EventoPort eventos;
    private final Clock reloj;

    public ConfirmarStockVentaUseCase(StockPort stock, ReservaPort reservas, LotePort lotes,
            EventoPort eventos, Clock reloj) {
        this.stock = stock;
        this.reservas = reservas;
        this.lotes = lotes;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Transactional
    public void confirmar(VentaConfirmada venta) {
        LocalDate hoy = LocalDate.now(reloj);
        List<LotesAsignados.Asignacion> asignadas = new ArrayList<>();

        for (VentaConfirmada.Linea linea : venta.lineas()) {
            // FEFO: primero el que vence antes. Obligatorio en farmacia.
            List<AsignacionLote> reparto = AsignadorFefo.asignar(
                    lotes.disponiblesParaFefo(linea.productoId(), venta.localId()),
                    linea.cantidad(), hoy);

            lotes.aplicarSalida(venta.ventaId(), linea.productoId(), venta.localId(),
                    venta.usuarioId(), reparto);

            stock.confirmarSalida(linea.productoId(), venta.localId(), linea.cantidad());

            reparto.forEach(a -> asignadas.add(new LotesAsignados.Asignacion(
                    linea.productoId(), a.loteId(), a.loteCodigo(),
                    a.fechaVencimiento(), a.cantidad())));
        }

        reservas.activasDeVenta(venta.ventaId())
                .forEach(r -> reservas.cambiarEstado(r.id(), EstadoReserva.CONFIRMADA));

        // ms-ventas necesita saber de que lote salio cada cosa para imprimir el
        // comprobante. La verdad de la trazabilidad se queda aca, en asignaciones_lote.
        eventos.publicar("venta", venta.ventaId(), Topicos.LOTES_ASIGNADOS, venta.localId(),
                new LotesAsignados(venta.ventaId(), venta.localId(), Instant.now(reloj), asignadas));

        log.info("Stock confirmado para venta {}: {} lineas, {} asignaciones de lote",
                venta.ventaId(), venta.lineas().size(), asignadas.size());
    }
}

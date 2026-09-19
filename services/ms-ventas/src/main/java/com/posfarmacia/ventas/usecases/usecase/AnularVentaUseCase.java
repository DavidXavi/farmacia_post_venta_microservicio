package com.posfarmacia.ventas.usecases.usecase;

import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.VentaAnulada;
import com.posfarmacia.ventas.domain.LineaVenta;
import com.posfarmacia.ventas.domain.Venta;
import com.posfarmacia.ventas.domain.VentaInvalidaException;
import com.posfarmacia.ventas.usecases.port.out.EventoPort;
import com.posfarmacia.ventas.usecases.port.out.SagaPort;
import com.posfarmacia.ventas.usecases.port.out.VentaPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anular una venta. Dispara la compensacion de la saga.
 *
 * <p>Tres cosas tienen que deshacerse en tres servicios distintos: inventario devuelve
 * los lotes, credito libera el monto y facturacion emite la nota de credito si el
 * comprobante ya salio. Cada uno compensa lo suyo cuando recibe el evento. No hay una
 * transaccion distribuida que revierta las tres de golpe, y no la hay a proposito:
 * seria un lock de dos fases sobre tres bases distintas, sostenido durante toda la
 * operacion. A 200 ventas/s eso no termina bien.
 */
@Service
public class AnularVentaUseCase {

    private static final Logger log = LoggerFactory.getLogger(AnularVentaUseCase.class);

    private final VentaPort ventas;
    private final SagaPort sagas;
    private final EventoPort eventos;

    public AnularVentaUseCase(VentaPort ventas, SagaPort sagas, EventoPort eventos) {
        this.ventas = ventas;
        this.sagas = sagas;
        this.eventos = eventos;
    }

    @Transactional
    public void anular(UUID ventaId, UUID usuarioId, String motivo, boolean comprobanteEmitido) {
        Venta venta = ventas.porId(ventaId)
                .orElseThrow(() -> new VentaInvalidaException("No existe la venta " + ventaId));

        venta.anular();
        ventas.guardar(venta);
        sagas.compensar(ventaId, motivo);

        List<VentaAnulada.Linea> lineas = venta.lineas().stream()
                .map(this::aLinea)
                .toList();

        eventos.publicar("venta", ventaId, Topicos.VENTAS_ANULADAS, venta.localId(),
                new VentaAnulada(ventaId, Instant.now(), venta.localId(), usuarioId,
                        motivo, comprobanteEmitido, venta.total(), lineas));

        log.warn("Venta {} anulada por {}: {}. Compensacion en camino.", ventaId, usuarioId, motivo);
    }

    private VentaAnulada.Linea aLinea(LineaVenta l) {
        return new VentaAnulada.Linea(l.productoId(), l.cantidad());
    }
}

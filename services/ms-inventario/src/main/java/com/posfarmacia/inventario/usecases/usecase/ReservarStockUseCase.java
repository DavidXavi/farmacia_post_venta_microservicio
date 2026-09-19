package com.posfarmacia.inventario.usecases.usecase;

import com.posfarmacia.contracts.api.ReservaRespuesta;
import com.posfarmacia.contracts.api.ReservaSolicitud;
import com.posfarmacia.inventario.domain.EstadoReserva;
import com.posfarmacia.inventario.usecases.port.out.ReservaPort;
import com.posfarmacia.inventario.usecases.port.out.StockPort;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apartar stock mientras el cajero arma la venta.
 *
 * <p>Es UNA de las dos unicas llamadas sincronas que pueden impedir una venta (la
 * otra es reservar credito). Es sincrona porque el cajero necesita saber ya si hay
 * stock: decirle "te aviso en un rato" con el cliente en el mostrador no es una opcion.
 *
 * <p>El presupuesto de latencia es 500 ms desde ms-ventas. Por eso aca no hay
 * consulta previa de disponibilidad, ni lectura de lotes, ni calculo de FEFO: es un
 * UPDATE condicional y un INSERT, y nada mas.
 */
@Service
public class ReservarStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReservarStockUseCase.class);

    private final StockPort stock;
    private final ReservaPort reservas;
    private final Duration ttl;

    public ReservarStockUseCase(StockPort stock, ReservaPort reservas,
            @Value("${pos.reserva.ttl-minutos:15}") int ttlMinutos) {
        this.stock = stock;
        this.reservas = reservas;
        this.ttl = Duration.ofMinutes(ttlMinutos);
    }

    @Transactional
    public ReservaRespuesta reservar(ReservaSolicitud solicitud) {
        Instant expira = Instant.now().plus(ttl);

        // Idempotencia primero: si esta venta ya aparto este producto, se devuelve la
        // reserva que habia. Sin esto, el reintento del POS aparta el doble y el
        // producto aparece agotado teniendolo.
        var existente = reservas.activasDeVenta(solicitud.ventaId()).stream()
                .filter(r -> r.productoId().equals(solicitud.productoId()))
                .findFirst();

        if (existente.isPresent()) {
            var r = existente.get();
            log.debug("Reserva ya existente para venta {} producto {}: se devuelve la misma",
                    solicitud.ventaId(), solicitud.productoId());
            return new ReservaRespuesta(r.id(), true,
                    stock.disponibleReal(solicitud.productoId(), solicitud.localId()),
                    r.expiraEn(), null);
        }

        boolean apartado = stock.intentarReservar(
                solicitud.productoId(), solicitud.localId(), solicitud.cantidad());

        if (!apartado) {
            int quedan = stock.disponibleReal(solicitud.productoId(), solicitud.localId());
            // No es excepcion: que se agote un producto es negocio normal, y el cajero
            // necesita el numero exacto para ofrecerle al cliente lo que si hay.
            return new ReservaRespuesta(null, false, quedan, null,
                    "Solo quedan " + quedan + " unidades");
        }

        var reserva = reservas.guardarSiNoExiste(solicitud.ventaId(), solicitud.productoId(),
                solicitud.localId(), solicitud.cantidad(), expira);

        return new ReservaRespuesta(reserva.id(), true,
                stock.disponibleReal(solicitud.productoId(), solicitud.localId()),
                expira, null);
    }

    /** Liberar una reserva concreta, por ejemplo cuando el cajero quita la linea. */
    @Transactional
    public void liberar(java.util.UUID reservaId) {
        reservas.porId(reservaId).ifPresent(r -> {
            if (r.estado() != EstadoReserva.ACTIVA) {
                return;   // idempotente: liberar dos veces no devuelve stock dos veces
            }
            stock.liberar(r.productoId(), r.localId(), r.cantidad());
            reservas.cambiarEstado(r.id(), EstadoReserva.LIBERADA);
        });
    }
}

package com.posfarmacia.inventario.domain;

/**
 * Ciclo de vida de una reserva de stock.
 *
 * <p>VENCIDA existe porque una caja se puede colgar a mitad de venta. Sin ese estado,
 * el stock apartado por una venta que nunca se confirmo quedaria bloqueado para
 * siempre y el producto apareceria agotado teniendolo en el anaquel.
 */
public enum EstadoReserva {
    ACTIVA,
    CONFIRMADA,
    LIBERADA,
    VENCIDA
}

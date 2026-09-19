package com.posfarmacia.inventario.domain;

import java.util.UUID;

/**
 * No alcanza el stock.
 *
 * <p>Lleva el disponible real adentro a proposito: el cajero necesita el numero
 * exacto para ofrecerle al cliente lo que si hay. Una excepcion que solo dice "no hay
 * stock" obliga a una segunda consulta justo en el momento en que el cliente espera.
 *
 * <p>Cuelga de {@code IllegalStateException}: no es que el pedido sea invalido, es que
 * en este momento no se puede. El manejador de errores la traduce a 409 con el mensaje.
 */
public class StockInsuficienteException extends IllegalStateException {

    private final UUID productoId;
    private final int solicitado;
    private final int disponible;

    public StockInsuficienteException(UUID productoId, int solicitado, int disponible) {
        super("Stock insuficiente para el producto " + productoId
                + ": se pidieron " + solicitado + " y hay " + disponible);
        this.productoId = productoId;
        this.solicitado = solicitado;
        this.disponible = disponible;
    }

    public UUID productoId() {
        return productoId;
    }

    public int solicitado() {
        return solicitado;
    }

    public int disponible() {
        return disponible;
    }
}

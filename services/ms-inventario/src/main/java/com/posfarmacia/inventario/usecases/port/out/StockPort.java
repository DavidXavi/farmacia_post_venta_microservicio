package com.posfarmacia.inventario.usecases.port.out;

import java.util.UUID;

/**
 * El contador de stock. Es la pieza que decide si el sistema aguanta 200 ventas/s o
 * se atasca en la primera campana.
 */
public interface StockPort {

    /**
     * Intenta apartar {@code cantidad} de forma atomica.
     *
     * <p>Devuelve false si no alcanza, sin lanzar excepcion: que un producto se agote
     * es un caso de negocio normal, no un error.
     *
     * @return true si se pudo apartar
     */
    boolean intentarReservar(UUID productoId, UUID localId, int cantidad);

    /**
     * Suelta una reserva que todavia no se cobro: baja {@code reservado} y el
     * disponible real vuelve a subir solo, porque es {@code disponible - reservado}.
     */
    void liberar(UUID productoId, UUID localId, int cantidad);

    /**
     * Devuelve al anaquel unidades que YA salieron.
     *
     * <p>No es lo mismo que {@link #liberar}: ahi la venta nunca se cobro y solo habia
     * que soltar lo apartado. Aca la salida ya se aplico, {@code reservado} ya bajo, y
     * lo que hay que reponer es {@code disponible}. Llamar a liberar en este caso deja
     * el contador sin tocar y las unidades desaparecen del sistema estando en el
     * anaquel: la anulacion sale verde y el inventario baja para siempre.
     */
    void devolver(UUID productoId, UUID localId, int cantidad);

    /** Convierte el apartado en salida definitiva: baja disponible y reservado a la vez. */
    void confirmarSalida(UUID productoId, UUID localId, int cantidad);

    int disponibleReal(UUID productoId, UUID localId);
}

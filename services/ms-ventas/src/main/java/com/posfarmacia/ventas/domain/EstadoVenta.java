package com.posfarmacia.ventas.domain;

/**
 * Estados de la venta desde la caja.
 *
 * <p>No hay estado "confirmando" ni "esperando inventario". La venta se confirma o no,
 * y lo que pasa despues con inventario, credito y SUNAT lo sigue la saga en su propia
 * tabla. Mezclar el estado de negocio con el estado de la coordinacion tecnica es
 * como se termina con una maquina de estados de catorce nodos que nadie entiende.
 */
public enum EstadoVenta {
    BORRADOR,
    CONFIRMADA,
    ANULADA
}

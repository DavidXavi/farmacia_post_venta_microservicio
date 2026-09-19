package com.posfarmacia.ventas.domain;

/**
 * Estado de la coordinacion entre servicios, separado del estado de negocio de la venta.
 *
 * <p>Sin esta tabla, una venta que quedo a medias porque un consumidor fallo es
 * invisible: nadie sabe si hay que compensar, ni que paso, ni cuantas van. Con ella,
 * la metrica de compensaciones tiene de donde salir y el reporte de "ventas sin
 * comprobante" se contesta con un SELECT.
 */
public enum EstadoSaga {
    EN_CURSO,
    COMPLETADA,
    COMPENSANDO,
    COMPENSADA
}

/**
 * DTO de las APIs que un microservicio expone a OTRO microservicio.
 *
 * <p>Viven aca y no dentro de cada servicio porque son el contrato: si ms-ventas y
 * ms-inventario tuvieran cada uno su propia copia de {@code ReservaSolicitud}, el dia
 * que cambie un campo se enteran en produccion.
 *
 * <p>Lo que NO va aca: entidades JPA, reglas de negocio, DTO que solo usa el frontend
 * de un servicio. Eso es acoplamiento disfrazado de reutilizacion.
 */
package com.posfarmacia.contracts.api;

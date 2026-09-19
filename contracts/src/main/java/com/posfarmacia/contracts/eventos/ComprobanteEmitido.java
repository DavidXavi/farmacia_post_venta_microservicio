package com.posfarmacia.contracts.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Comprobante ya aceptado por SUNAT. Cierra la saga en ms-ventas.
 *
 * <p>Puede llegar segundos despues de la venta o al dia siguiente, si SUNAT estuvo
 * caida. Esa es justamente la razon de que facturacion sea un servicio aparte: su
 * lentitud nunca llega a la caja.
 */
public record ComprobanteEmitido(
        UUID comprobanteId,
        UUID ventaId,
        UUID localId,
        String tipo,
        String serie,
        int correlativo,
        String estadoSunat,
        Instant fechaEmision) {
}

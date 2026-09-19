package com.posfarmacia.facturacion.usecases.port.out;

import java.util.UUID;

/**
 * El tercero lento y poco confiable.
 *
 * <p>Es un puerto y no una llamada directa porque detras puede estar SUNAT, un OSE, o
 * el simulador que se usa en desarrollo. Que sea intercambiable no es purismo: probar
 * el comportamiento del sistema cuando SUNAT falla exige poder hacerla fallar a voluntad.
 */
public interface SunatPort {

    record Respuesta(boolean aceptado, String codigo, String mensaje) {
    }

    Respuesta enviar(UUID comprobanteId, String tipo, String serie, int correlativo,
            java.math.BigDecimal montoTotal);
}

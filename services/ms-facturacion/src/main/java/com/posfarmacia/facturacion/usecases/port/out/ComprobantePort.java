package com.posfarmacia.facturacion.usecases.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ComprobantePort {

    record Pendiente(UUID id, UUID ventaId, UUID localId, String tipo, String serie,
                     int correlativo, BigDecimal montoTotal, Instant fechaEmision) {
    }

    /** Correlativo siguiente de la serie. Atomico: la numeracion no admite repetidos. */
    int siguienteCorrelativo(String serie);

    UUID guardarPendiente(UUID ventaId, UUID localId, String tipo, String serie,
            int correlativo, BigDecimal montoTotal, Instant fecha);

    List<Pendiente> pendientes(int limite);

    void registrarEnvio(UUID comprobanteId, boolean exitoso, String codigo, String mensaje);

    void marcarAceptado(UUID comprobanteId);

    /**
     * El comprobante emitido por una venta.
     *
     * <p>Lo necesita la devolucion: la nota de credito se emite contra un comprobante
     * concreto, y de ahi sale tambien el local, que facturacion conoce y ms-ventas no
     * expone en su vista de venta.
     */
    java.util.Optional<Pendiente> porVentaId(UUID ventaId);
}

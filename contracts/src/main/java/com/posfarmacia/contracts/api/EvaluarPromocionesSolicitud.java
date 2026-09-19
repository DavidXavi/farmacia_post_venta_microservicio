package com.posfarmacia.contracts.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Evaluacion de promociones para una venta completa, no linea por linea.
 *
 * <p>El lote completo es a proposito: una venta de cinco lineas seria cinco llamadas
 * a ms-promociones, y con 200 ventas/s eso son 1000 req/s extra solo por no haber
 * pedido las cinco de una. El fan-out es lo que mata bajo carga.
 */
public record EvaluarPromocionesSolicitud(
        UUID ventaId,
        UUID clienteId,
        List<Linea> lineas) {

    public record Linea(UUID productoId, int cantidad, BigDecimal precioUnitario) {
    }
}

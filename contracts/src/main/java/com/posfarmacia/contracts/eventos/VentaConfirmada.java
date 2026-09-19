package com.posfarmacia.contracts.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El evento central del sistema. Lo publica ms-ventas cuando la venta ya quedo
 * confirmada en su base, via outbox, y lo consumen cuatro servicios a la vez:
 * inventario (confirma reservas y aplica FEFO), credito (convierte la reserva en
 * cargo firme), facturacion (emite el CPE) y reportes (actualiza el read model).
 *
 * <p>Trae dentro el nombre del local, del vendedor y del cliente aunque ms-ventas no
 * sea el dueno de esos datos. Es duplicacion deliberada: sin ella, ms-reportes
 * tendria que llamar a tres servicios por cada venta que procesa, y el read model
 * dejaria de ser el consulta-rapida que justifica su existencia.
 */
public record VentaConfirmada(
        UUID ventaId,
        Instant fecha,
        UUID localId,
        String localNombre,
        UUID cajaId,
        UUID usuarioId,
        String usuarioNombre,
        UUID clienteId,
        String clienteNombre,
        UUID convenioSeguroId,
        UUID lineaCreditoId,
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal impuesto,
        BigDecimal total,
        String tipoComprobante,
        List<Linea> lineas) {

    public record Linea(
            UUID detalleVentaId,
            UUID productoId,
            String productoNombre,
            UUID categoriaId,
            String categoriaNombre,
            int cantidad,
            BigDecimal precioUnitario,
            BigDecimal descuento,
            BigDecimal totalLinea,
            UUID promocionAplicadaId,
            UUID recetaId) {
    }
}

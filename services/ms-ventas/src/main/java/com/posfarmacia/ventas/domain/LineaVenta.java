package com.posfarmacia.ventas.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Una linea de la venta.
 *
 * <p>Guarda nombreProducto y precioUnitario como COPIA, no como referencia viva al
 * catalogo. Es duplicacion deliberada y es lo correcto: el precio de la venta es el
 * del momento de la venta aunque manana cambie la lista, y el comprobante tiene que
 * seguir imprimiendose aunque el producto se haya dado de baja.
 *
 * <p>Esa copia es tambien lo que hace posible el corte: sin ella, imprimir una boleta
 * de hace un ano obligaria a consultar catalogo, y una venta vieja dependeria para
 * siempre de que otro servicio este vivo.
 */
public record LineaVenta(
        UUID id,
        UUID ventaId,
        UUID productoId,
        String nombreProducto,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal tasaImpuesto,
        BigDecimal descuento,
        UUID promocionAplicadaId,
        UUID recetaId) {

    public LineaVenta {
        if (cantidad <= 0) {
            throw new VentaInvalidaException("La cantidad debe ser mayor que cero");
        }
        if (precioUnitario == null || precioUnitario.signum() < 0) {
            throw new VentaInvalidaException("El precio unitario no puede ser negativo");
        }
        descuento = descuento == null ? BigDecimal.ZERO : descuento;
    }

    /** Precio por cantidad, menos el descuento. Es la base sobre la que se calcula el IGV. */
    public BigDecimal baseImponible() {
        return precioUnitario
                .multiply(BigDecimal.valueOf(cantidad))
                .subtract(descuento)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal impuesto() {
        return baseImponible()
                .multiply(tasaImpuesto)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal total() {
        return baseImponible().add(impuesto()).setScale(2, RoundingMode.HALF_UP);
    }
}

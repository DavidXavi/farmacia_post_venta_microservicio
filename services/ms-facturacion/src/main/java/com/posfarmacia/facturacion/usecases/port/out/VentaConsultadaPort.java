package com.posfarmacia.facturacion.usecases.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * La venta que se esta devolviendo, tal como la conoce ms-ventas.
 *
 * <p>Facturacion no guarda las lineas de cada venta y no deberia: a 200 ventas/s de
 * cinco lineas serian 1000 filas por segundo escritas solo por si algun dia hay una
 * devolucion. Una devolucion es un camino frio, de unas pocas al dia, asi que se paga
 * una llamada cuando ocurre en vez de un costo permanente en el camino caliente.
 *
 * <p>Se pregunta en vez de confiar en lo que manda la pantalla porque el monto a
 * devolver es dinero que sale de la caja. Si lo decidiera el cliente, cualquiera con
 * la consola del navegador abierta podria devolver por mas de lo que compro.
 */
public interface VentaConsultadaPort {

    record LineaVendida(UUID detalleVentaId, UUID productoId, int cantidad,
                        BigDecimal totalLinea) {

        /** Lo que costo cada unidad, impuesto incluido. Es la base del monto a devolver. */
        public BigDecimal precioUnitarioConImpuesto() {
            return cantidad <= 0
                    ? BigDecimal.ZERO
                    : totalLinea.divide(BigDecimal.valueOf(cantidad), 2,
                            java.math.RoundingMode.HALF_UP);
        }
    }

    record VentaVendida(UUID ventaId, UUID localId, String estado, List<LineaVendida> lineas) {
    }

    /** Lanza si ms-ventas no responde: sin la venta no se puede calcular la devolucion. */
    VentaVendida porId(UUID ventaId);
}

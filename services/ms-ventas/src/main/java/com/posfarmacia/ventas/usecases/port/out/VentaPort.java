package com.posfarmacia.ventas.usecases.port.out;

import com.posfarmacia.ventas.domain.Venta;
import java.util.Optional;
import java.util.UUID;

public interface VentaPort {

    void guardar(Venta venta);

    Optional<Venta> porId(UUID ventaId);

    /** Registra un pago de la venta. Idempotente por (venta, forma de pago, monto). */
    void registrarPago(UUID ventaId, UUID formaPagoId, java.math.BigDecimal monto,
            String codigoAutorizacion);

    /** Suma de los pagos registrados para la venta. */
    java.math.BigDecimal totalPagado(UUID ventaId);

    /** Guarda la copia de lotes que llega por evento, solo para imprimir el comprobante. */
    void guardarLotesAsignados(UUID ventaId, UUID productoId, UUID loteId, int cantidad);
}

package com.posfarmacia.contracts.eventos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resultado del FEFO. Lo publica ms-inventario despues de confirmar la venta y lo
 * consume ms-ventas para guardar la copia que se imprime en el comprobante.
 *
 * <p>La asignacion se hace aca y no en el camino critico a proposito: el cajero no
 * necesita saber de que lote sale el producto hasta el despacho, y meterlo en la
 * ruta caliente agregaria una escritura mas a la operacion que ya tiene contencion.
 */
public record LotesAsignados(
        UUID ventaId,
        UUID localId,
        Instant fecha,
        List<Asignacion> asignaciones) {

    public record Asignacion(
            UUID productoId,
            UUID loteId,
            String loteCodigo,
            java.time.LocalDate fechaVencimiento,
            int cantidad) {
    }
}

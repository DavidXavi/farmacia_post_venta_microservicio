package com.posfarmacia.reportes.usecases.port.out;

import com.posfarmacia.contracts.eventos.VentaConfirmada;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * El read model. Solo escritura por eventos y lectura por consultas: no hay un solo
 * endpoint que mute algo desde fuera.
 *
 * <p>Este servicio es el que mas capacidad de consulta le agrega al sistema, y no por
 * ser un microservicio mas: por sacar los escaneos analiticos de la base transaccional.
 * Un reporte gerencial que recorre tres meses de ventas, corriendo contra pg_ventas,
 * le roba IO a las cajas justo a fin de mes, que es cuando mas se vende y cuando mas
 * reportes se piden.
 */
public interface ReadModelPort {

    record VentaDiaria(LocalDate dia, UUID localId, UUID productoId, String productoNombre,
                       int unidades, BigDecimal importe, int transacciones) {
    }

    record IncentivoVendedor(UUID usuarioId, String usuarioNombre, int unidades,
                             BigDecimal montoTotal) {
    }

    /** Proyecta la venta a las tres tablas del read model en una transaccion. */
    void proyectar(VentaConfirmada venta);

    void anular(UUID ventaId);

    List<VentaDiaria> ventasDiarias(LocalDate desde, LocalDate hasta, UUID localId);

    List<IncentivoVendedor> incentivos(LocalDate desde, LocalDate hasta);
}

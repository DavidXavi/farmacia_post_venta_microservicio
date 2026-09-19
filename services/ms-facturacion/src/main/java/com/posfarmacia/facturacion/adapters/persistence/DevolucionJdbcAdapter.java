package com.posfarmacia.facturacion.adapters.persistence;

import com.posfarmacia.facturacion.usecases.port.out.DevolucionPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Devoluciones y notas de credito, en SQL explicito. */
@Repository
public class DevolucionJdbcAdapter implements DevolucionPort {

    private static final String YA_DEVUELTO = """
            SELECT d.detalle_venta_id, SUM(d.cantidad) AS cantidad
              FROM detalle_devoluciones d
              JOIN devoluciones v ON v.id = d.devolucion_id
             WHERE v.venta_id = ?
             GROUP BY d.detalle_venta_id
            """;

    private final JdbcClient jdbc;

    public DevolucionJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertarDevolucion(UUID id, UUID ventaId, UUID localId, UUID usuarioId,
            String motivo, List<LineaDevuelta> lineas) {
        jdbc.sql("""
                        INSERT INTO devoluciones (id, venta_id, local_id, usuario_id, motivo)
                        VALUES (?, ?, ?, ?, ?)
                        """)
                .param(id).param(ventaId).param(localId).param(usuarioId).param(motivo)
                .update();

        for (LineaDevuelta l : lineas) {
            jdbc.sql("""
                            INSERT INTO detalle_devoluciones
                                   (id, devolucion_id, detalle_venta_id, producto_id,
                                    cantidad, monto_devuelto)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """)
                    .param(UUID.randomUUID()).param(id).param(l.detalleVentaId())
                    .param(l.productoId()).param(l.cantidad()).param(l.montoDevuelto())
                    .update();
        }
    }

    @Override
    public Map<UUID, Integer> yaDevueltoPorLinea(UUID ventaId) {
        Map<UUID, Integer> porLinea = new HashMap<>();
        jdbc.sql(YA_DEVUELTO).param(ventaId)
                .query((rs, fila) -> {
                    porLinea.put(rs.getObject("detalle_venta_id", UUID.class),
                            rs.getInt("cantidad"));
                    return null;
                })
                .list();
        return porLinea;
    }

    @Override
    public void insertarNotaCredito(UUID id, UUID ventaId, UUID comprobanteId, UUID localId,
            UUID usuarioId, String motivo, BigDecimal montoTotal, Instant fecha) {
        jdbc.sql("""
                        INSERT INTO notas_credito (id, venta_id, comprobante_id, local_id,
                                                   usuario_id, motivo, monto_total, fecha)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .param(id).param(ventaId).param(comprobanteId).param(localId)
                .param(usuarioId).param(motivo).param(montoTotal)
                .param(java.sql.Timestamp.from(fecha))
                .update();
    }
}

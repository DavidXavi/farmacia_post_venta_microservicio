package com.posfarmacia.inventario.adapters.persistence;

import com.posfarmacia.inventario.domain.AsignacionLote;
import com.posfarmacia.inventario.domain.Lote;
import com.posfarmacia.inventario.usecases.port.out.LotePort;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LoteJdbcAdapter implements LotePort {

    /** Consulta que usa exactamente el indice parcial idx_lotes_fefo de la migracion. */
    private static final String DISPONIBLES = """
            SELECT id, codigo, producto_id, local_id, fecha_vencimiento, cantidad_disponible, estado
              FROM lotes
             WHERE producto_id = ? AND local_id = ?
               AND estado = 'DISPONIBLE' AND cantidad_disponible > 0
             ORDER BY fecha_vencimiento, id
            """;

    private static final String DESCONTAR = """
            UPDATE lotes SET cantidad_disponible = cantidad_disponible - ?
             WHERE id = ? AND cantidad_disponible >= ?
            """;

    private static final String MOVIMIENTO = """
            INSERT INTO movimientos_inventario
                (id, lote_id, producto_id, local_id, tipo, cantidad, usuario_id, referencia)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String ASIGNACION = """
            INSERT INTO asignaciones_lote (id, venta_id, producto_id, lote_id, cantidad)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String ASIGNACIONES_DE = """
            SELECT lote_id, cantidad FROM asignaciones_lote
             WHERE venta_id = ? AND producto_id = ?
            """;

    private static final String DEVOLVER = """
            UPDATE lotes SET cantidad_disponible = cantidad_disponible + ? WHERE id = ?
            """;

    private static final String BORRAR_ASIGNACION = """
            DELETE FROM asignaciones_lote WHERE venta_id = ? AND producto_id = ?
            """;

    private static final RowMapper<Lote> MAPPER = (ResultSet rs, int fila) -> new Lote(
            rs.getObject("id", UUID.class),
            rs.getString("codigo"),
            rs.getObject("producto_id", UUID.class),
            rs.getObject("local_id", UUID.class),
            rs.getObject("fecha_vencimiento", java.time.LocalDate.class),
            rs.getInt("cantidad_disponible"),
            rs.getString("estado"));

    private final JdbcClient jdbc;

    public LoteJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Lote> disponiblesParaFefo(UUID productoId, UUID localId) {
        return jdbc.sql(DISPONIBLES).param(productoId).param(localId).query(MAPPER).list();
    }

    @Override
    public void aplicarSalida(UUID ventaId, UUID productoId, UUID localId, UUID usuarioId,
            List<AsignacionLote> asignaciones) {
        for (AsignacionLote a : asignaciones) {
            int filas = jdbc.sql(DESCONTAR)
                    .param(a.cantidad()).param(a.loteId()).param(a.cantidad())
                    .update();
            if (filas != 1) {
                // El lote cambio entre que se calculo el FEFO y que se aplico. Fallar
                // aqui hace que el consumidor reintente y recalcule con datos frescos,
                // que es lo correcto: descontar a la fuerza dejaria el lote en negativo.
                throw new IllegalStateException(
                        "El lote " + a.loteId() + " ya no tiene " + a.cantidad()
                                + " unidades: se recalcula el FEFO en el reintento");
            }

            jdbc.sql(MOVIMIENTO)
                    .param(UUID.randomUUID()).param(a.loteId()).param(productoId).param(localId)
                    .param("SALIDA_VENTA").param(a.cantidad()).param(usuarioId)
                    .param(ventaId.toString())
                    .update();

            jdbc.sql(ASIGNACION)
                    .param(UUID.randomUUID()).param(ventaId).param(productoId)
                    .param(a.loteId()).param(a.cantidad())
                    .update();
        }
    }

    @Override
    public void revertirSalida(UUID ventaId, UUID productoId, UUID localId, UUID usuarioId) {
        record Devolucion(UUID loteId, int cantidad) {
        }

        List<Devolucion> aDevolver = jdbc.sql(ASIGNACIONES_DE)
                .param(ventaId).param(productoId)
                .query((rs, fila) -> new Devolucion(rs.getObject("lote_id", UUID.class),
                        rs.getInt("cantidad")))
                .list();

        // Se devuelve al lote exacto del que salio, no a cualquiera. Devolver a otro
        // lote rompe la trazabilidad de vencimiento: el sistema creeria tener unidades
        // de un lote que fisicamente no estan ahi.
        aDevolver.forEach(d -> {
            jdbc.sql(DEVOLVER).param(d.cantidad()).param(d.loteId()).update();
            jdbc.sql(MOVIMIENTO)
                    .param(UUID.randomUUID()).param(d.loteId()).param(productoId).param(localId)
                    .param("DEVOLUCION").param(d.cantidad()).param(usuarioId)
                    .param(ventaId.toString())
                    .update();
        });

        jdbc.sql(BORRAR_ASIGNACION).param(ventaId).param(productoId).update();
    }
}

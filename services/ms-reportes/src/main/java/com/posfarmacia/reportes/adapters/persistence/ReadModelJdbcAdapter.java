package com.posfarmacia.reportes.adapters.persistence;

import com.posfarmacia.contracts.eventos.VentaConfirmada;
import com.posfarmacia.reportes.usecases.port.out.ReadModelPort;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReadModelJdbcAdapter implements ReadModelPort {

    private static final String CABECERA = """
            INSERT INTO rm_ventas (venta_id, fecha, local_id, local_nombre, caja_id, usuario_id,
                                   usuario_nombre, cliente_id, cliente_nombre, subtotal,
                                   descuento, impuesto, total, cantidad_lineas, estado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMADA')
            ON CONFLICT (venta_id, fecha) DO NOTHING
            """;

    private static final String LINEA = """
            INSERT INTO rm_venta_lineas (id, venta_id, fecha, local_id, usuario_id, producto_id,
                                         producto_nombre, categoria_id, categoria_nombre,
                                         cantidad, precio_unitario, descuento, total_linea)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """;

    /**
     * El agregado diario se mantiene con UPSERT en el momento de proyectar, no
     * recalculando al consultar.
     *
     * <p>Esa es toda la diferencia entre un tablero que responde en milisegundos y uno
     * que agrega millones de lineas cada vez que alguien abre la pagina. El costo se
     * paga una vez por venta, en segundo plano, en vez de una vez por consulta, en
     * primer plano y con un gerente esperando.
     */
    private static final String DIARIO = """
            INSERT INTO rm_ventas_diarias (dia, local_id, producto_id, unidades, importe, transacciones)
            VALUES (?, ?, ?, ?, ?, 1)
            ON CONFLICT (dia, local_id, producto_id) DO UPDATE SET
                unidades = rm_ventas_diarias.unidades + EXCLUDED.unidades,
                importe = rm_ventas_diarias.importe + EXCLUDED.importe,
                transacciones = rm_ventas_diarias.transacciones + 1
            """;

    private static final String ANULAR = """
            UPDATE rm_ventas SET estado = 'ANULADA' WHERE venta_id = ?
            """;

    private static final String CONSULTA_DIARIA = """
            SELECT d.dia, d.local_id, d.producto_id,
                   COALESCE(MAX(l.producto_nombre), '') AS producto_nombre,
                   d.unidades, d.importe, d.transacciones
              FROM rm_ventas_diarias d
              LEFT JOIN rm_venta_lineas l ON l.producto_id = d.producto_id
             WHERE d.dia BETWEEN ? AND ?
               AND (?::uuid IS NULL OR d.local_id = ?::uuid)
             GROUP BY d.dia, d.local_id, d.producto_id, d.unidades, d.importe, d.transacciones
             ORDER BY d.dia DESC, d.importe DESC
            """;

    private static final String CONSULTA_INCENTIVOS = """
            SELECT v.usuario_id,
                   COALESCE(MAX(v.usuario_nombre), '') AS usuario_nombre,
                   COALESCE(SUM(i.cantidad), 0) AS unidades,
                   COALESCE(SUM(i.monto_calculado), 0) AS monto_total
              FROM incentivos_venta i
              JOIN rm_ventas v ON v.venta_id = i.venta_id
             WHERE i.fecha::date BETWEEN ? AND ?
             GROUP BY v.usuario_id
             ORDER BY monto_total DESC
            """;

    private final JdbcClient jdbc;

    public ReadModelJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void proyectar(VentaConfirmada v) {
        var marca = java.sql.Timestamp.from(v.fecha());

        jdbc.sql(CABECERA)
                .param(v.ventaId()).param(marca).param(v.localId()).param(v.localNombre())
                .param(v.cajaId()).param(v.usuarioId()).param(v.usuarioNombre())
                .param(v.clienteId()).param(v.clienteNombre())
                .param(v.subtotal()).param(v.descuento()).param(v.impuesto()).param(v.total())
                .param(v.lineas().size())
                .update();

        LocalDate dia = v.fecha().atZone(ZoneId.systemDefault()).toLocalDate();

        for (var l : v.lineas()) {
            jdbc.sql(LINEA)
                    .param(l.detalleVentaId()).param(v.ventaId()).param(marca)
                    .param(v.localId()).param(v.usuarioId()).param(l.productoId())
                    .param(l.productoNombre()).param(l.categoriaId()).param(l.categoriaNombre())
                    .param(l.cantidad()).param(l.precioUnitario()).param(l.descuento())
                    .param(l.totalLinea())
                    .update();

            jdbc.sql(DIARIO)
                    .param(dia).param(v.localId()).param(l.productoId())
                    .param(l.cantidad()).param(l.totalLinea())
                    .update();
        }
    }

    @Override
    public void anular(UUID ventaId) {
        jdbc.sql(ANULAR).param(ventaId).update();
    }

    @Override
    public List<VentaDiaria> ventasDiarias(LocalDate desde, LocalDate hasta, UUID localId) {
        return jdbc.sql(CONSULTA_DIARIA)
                .param(desde).param(hasta).param(localId).param(localId)
                .query((rs, i) -> new VentaDiaria(
                        rs.getObject("dia", LocalDate.class),
                        rs.getObject("local_id", UUID.class),
                        rs.getObject("producto_id", UUID.class),
                        rs.getString("producto_nombre"),
                        rs.getInt("unidades"),
                        rs.getBigDecimal("importe"),
                        rs.getInt("transacciones")))
                .list();
    }

    @Override
    public List<IncentivoVendedor> incentivos(LocalDate desde, LocalDate hasta) {
        return jdbc.sql(CONSULTA_INCENTIVOS)
                .param(desde).param(hasta)
                .query((rs, i) -> new IncentivoVendedor(
                        rs.getObject("usuario_id", UUID.class),
                        rs.getString("usuario_nombre"),
                        rs.getInt("unidades"),
                        rs.getBigDecimal("monto_total")))
                .list();
    }
}

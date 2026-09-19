package com.posfarmacia.ventas.adapters.persistence;

import com.posfarmacia.ventas.domain.EstadoVenta;
import com.posfarmacia.ventas.domain.LineaVenta;
import com.posfarmacia.ventas.domain.Venta;
import com.posfarmacia.ventas.usecases.port.out.VentaPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistencia de la venta.
 *
 * <p>ponytail: JdbcClient, no JPA. La venta es un agregado de dos tablas que siempre
 * se leen por id y siempre se escriben completas. JPA aportaria cache de primer nivel,
 * lazy loading y dirty checking, tres cosas que aqui no se usan y que a cambio traen
 * el N+1 accidental y la sesion abierta mas tiempo del necesario.
 *
 * <p>La tabla ventas esta particionada por mes, asi que toda consulta lleva la fecha
 * ademas del id: sin ella Postgres tiene que mirar las trece particiones en vez de una.
 */
@Repository
public class VentaJdbcAdapter implements VentaPort {

    private static final String UPSERT_VENTA = """
            INSERT INTO ventas (id, fecha, local_id, caja_id, sesion_caja_id, usuario_id,
                                cliente_id, convenio_seguro_id, linea_credito_id, estado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id, fecha) DO UPDATE SET
                cliente_id = EXCLUDED.cliente_id,
                convenio_seguro_id = EXCLUDED.convenio_seguro_id,
                linea_credito_id = EXCLUDED.linea_credito_id,
                estado = EXCLUDED.estado,
                version = ventas.version + 1
            """;

    private static final String BORRAR_LINEAS = "DELETE FROM detalles_venta WHERE venta_id = ?";

    private static final String INSERTAR_LINEA = """
            INSERT INTO detalles_venta (id, venta_id, producto_id, cantidad, precio_unitario,
                                        tasa_impuesto, promocion_aplicada_id, receta_id,
                                        descuento_monto, nombre_producto)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String VENTA_POR_ID = """
            SELECT id, fecha, local_id, caja_id, sesion_caja_id, usuario_id, cliente_id,
                   convenio_seguro_id, linea_credito_id, estado
              FROM ventas WHERE id = ?
            """;

    private static final String LINEAS_DE = """
            SELECT id, venta_id, producto_id, nombre_producto, cantidad, precio_unitario,
                   tasa_impuesto, descuento_monto, promocion_aplicada_id, receta_id
              FROM detalles_venta WHERE venta_id = ?
            """;

    private static final String LOTE_ASIGNADO = """
            INSERT INTO detalle_venta_lotes (id, detalle_venta_id, lote_id, cantidad_tomada)
            SELECT ?, d.id, ?, ?
              FROM detalles_venta d
             WHERE d.venta_id = ? AND d.producto_id = ?
            """;

    private final JdbcClient jdbc;

    public VentaJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(Venta venta) {
        jdbc.sql(UPSERT_VENTA)
                .param(venta.id())
                .param(java.sql.Timestamp.from(venta.fecha()))
                .param(venta.localId()).param(venta.cajaId()).param(venta.sesionCajaId())
                .param(venta.usuarioId()).param(venta.clienteId())
                .param(venta.convenioSeguroId()).param(venta.lineaCreditoId())
                .param(venta.estado().name())
                .update();

        // Borrar y reinsertar las lineas es mas simple que reconciliar altas, bajas y
        // cambios, y una venta tiene cinco lineas, no cinco mil. Si alguna vez las
        // ventas tuvieran cientos de lineas, esto se cambia por un diff.
        jdbc.sql(BORRAR_LINEAS).param(venta.id()).update();
        for (LineaVenta l : venta.lineas()) {
            jdbc.sql(INSERTAR_LINEA)
                    .param(l.id()).param(l.ventaId()).param(l.productoId()).param(l.cantidad())
                    .param(l.precioUnitario()).param(l.tasaImpuesto())
                    .param(l.promocionAplicadaId()).param(l.recetaId())
                    .param(l.descuento()).param(l.nombreProducto())
                    .update();
        }
    }

    @Override
    public Optional<Venta> porId(UUID ventaId) {
        return jdbc.sql(VENTA_POR_ID).param(ventaId)
                .query((rs, fila) -> new Venta(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("fecha").toInstant(),
                        rs.getObject("local_id", UUID.class),
                        rs.getObject("caja_id", UUID.class),
                        rs.getObject("sesion_caja_id", UUID.class),
                        rs.getObject("usuario_id", UUID.class),
                        rs.getObject("cliente_id", UUID.class),
                        rs.getObject("convenio_seguro_id", UUID.class),
                        rs.getObject("linea_credito_id", UUID.class),
                        EstadoVenta.valueOf(rs.getString("estado")),
                        lineasDe(ventaId)))
                .optional();
    }

    private List<LineaVenta> lineasDe(UUID ventaId) {
        return jdbc.sql(LINEAS_DE).param(ventaId)
                .query((rs, fila) -> new LineaVenta(
                        rs.getObject("id", UUID.class),
                        rs.getObject("venta_id", UUID.class),
                        rs.getObject("producto_id", UUID.class),
                        rs.getString("nombre_producto"),
                        rs.getInt("cantidad"),
                        rs.getBigDecimal("precio_unitario"),
                        rs.getBigDecimal("tasa_impuesto"),
                        rs.getBigDecimal("descuento_monto"),
                        rs.getObject("promocion_aplicada_id", UUID.class),
                        rs.getObject("receta_id", UUID.class)))
                .list();
    }

    @Override
    public void registrarPago(UUID ventaId, UUID formaPagoId, java.math.BigDecimal monto,
            String codigoAutorizacion) {
        jdbc.sql("""
                INSERT INTO pagos (id, venta_id, forma_pago_id, monto, codigo_autorizacion)
                VALUES (?, ?, ?, ?, ?)
                """)
                .param(UUID.randomUUID()).param(ventaId).param(formaPagoId)
                .param(monto).param(codigoAutorizacion)
                .update();
    }

    @Override
    public java.math.BigDecimal totalPagado(UUID ventaId) {
        return jdbc.sql("SELECT COALESCE(SUM(monto), 0) FROM pagos WHERE venta_id = ?")
                .param(ventaId)
                .query(java.math.BigDecimal.class)
                .single();
    }

    @Override
    public void guardarLotesAsignados(UUID ventaId, UUID productoId, UUID loteId, int cantidad) {
        jdbc.sql(LOTE_ASIGNADO)
                .param(UUID.randomUUID()).param(loteId).param(cantidad)
                .param(ventaId).param(productoId)
                .update();
    }
}

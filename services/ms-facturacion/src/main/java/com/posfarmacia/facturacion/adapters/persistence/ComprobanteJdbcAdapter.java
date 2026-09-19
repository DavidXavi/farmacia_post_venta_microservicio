package com.posfarmacia.facturacion.adapters.persistence;

import com.posfarmacia.facturacion.usecases.port.out.ComprobantePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ComprobanteJdbcAdapter implements ComprobantePort {

    /**
     * UPDATE ... RETURNING: incrementa y devuelve en una sola operacion atomica.
     *
     * <p>Leer el ultimo y sumar uno desde la aplicacion produciria correlativos repetidos
     * en cuanto dos pods emitan a la vez. Ante SUNAT, dos comprobantes con el mismo
     * numero no son un bug que se arregla despues: son una contingencia tributaria.
     */
    private static final String CORRELATIVO = """
            UPDATE series_comprobante SET ultimo = ultimo + 1 WHERE serie = ? RETURNING ultimo
            """;

    private static final String GUARDAR = """
            INSERT INTO comprobantes (id, venta_id, local_id, tipo, serie, correlativo,
                                      monto_total, fecha_emision, estado_sunat)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE')
            ON CONFLICT DO NOTHING
            """;

    private static final String PENDIENTES = """
            SELECT id, venta_id, local_id, tipo, serie, correlativo, monto_total, fecha_emision
              FROM comprobantes
             WHERE estado_sunat IN ('PENDIENTE', 'OBSERVADO')
             ORDER BY fecha_emision
             LIMIT ?
             FOR UPDATE SKIP LOCKED
            """;

    private static final String ENVIO = """
            INSERT INTO envios_sunat (id, comprobante_id, intento, respondido_en,
                                      codigo_sunat, mensaje, exitoso)
            SELECT ?, ?, COALESCE(MAX(intento), 0) + 1, now(), ?, ?, ?
              FROM envios_sunat WHERE comprobante_id = ?
            """;

    private static final String ACEPTAR = """
            UPDATE comprobantes SET estado_sunat = 'ACEPTADO' WHERE id = ?
            """;

    private final JdbcClient jdbc;

    public ComprobanteJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int siguienteCorrelativo(String serie) {
        return jdbc.sql(CORRELATIVO).param(serie).query(Integer.class).single();
    }

    @Override
    public UUID guardarPendiente(UUID ventaId, UUID localId, String tipo, String serie,
            int correlativo, BigDecimal montoTotal, Instant fecha) {
        UUID id = UUID.randomUUID();
        jdbc.sql(GUARDAR)
                .param(id).param(ventaId).param(localId).param(tipo).param(serie)
                .param(correlativo).param(montoTotal).param(java.sql.Timestamp.from(fecha))
                .update();
        return id;
    }

    @Override
    public List<Pendiente> pendientes(int limite) {
        return jdbc.sql(PENDIENTES).param(limite)
                .query((rs, i) -> new Pendiente(
                        rs.getObject("id", UUID.class),
                        rs.getObject("venta_id", UUID.class),
                        rs.getObject("local_id", UUID.class),
                        rs.getString("tipo"),
                        rs.getString("serie"),
                        rs.getInt("correlativo"),
                        rs.getBigDecimal("monto_total"),
                        rs.getTimestamp("fecha_emision").toInstant()))
                .list();
    }

    @Override
    public void registrarEnvio(UUID comprobanteId, boolean exitoso, String codigo, String mensaje) {
        jdbc.sql(ENVIO)
                .param(UUID.randomUUID()).param(comprobanteId)
                .param(codigo).param(mensaje).param(exitoso).param(comprobanteId)
                .update();
    }

    @Override
    public java.util.Optional<Pendiente> porVentaId(UUID ventaId) {
        return jdbc.sql("""
                        SELECT id, venta_id, local_id, tipo, serie, correlativo,
                               monto_total, fecha_emision
                          FROM comprobantes
                         WHERE venta_id = ?
                         ORDER BY fecha_emision DESC
                         LIMIT 1
                        """)
                .param(ventaId)
                .query((rs, fila) -> new Pendiente(
                        rs.getObject("id", UUID.class),
                        rs.getObject("venta_id", UUID.class),
                        rs.getObject("local_id", UUID.class),
                        rs.getString("tipo"),
                        rs.getString("serie"),
                        rs.getInt("correlativo"),
                        rs.getBigDecimal("monto_total"),
                        rs.getTimestamp("fecha_emision").toInstant()))
                .optional();
    }

    @Override
    public void marcarAceptado(UUID comprobanteId) {
        jdbc.sql(ACEPTAR).param(comprobanteId).update();
    }
}

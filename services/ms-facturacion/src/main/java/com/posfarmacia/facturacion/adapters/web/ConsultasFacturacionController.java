package com.posfarmacia.facturacion.adapters.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de lectura de ms-facturacion.
 *
 * <p>Devuelve mapas y no DTO tipados a propósito: son listados para pantallas de
 * administración, sin lógica y sin contrato entre servicios que proteger. Un record por
 * cada uno serían 3 clases que no aportan nada y que hay que cambiar cada vez que la
 * pantalla muestra una columna más. Los DTO tipados viven en {@code contracts} y son
 * los que SÍ cruzan servicios.
 *
 * <p>SQL explícito, igual que el resto de la persistencia del proyecto: son consultas
 * planas sobre tablas propias.
 */
@RestController
public class ConsultasFacturacionController {

    private final JdbcClient jdbc;

    public ConsultasFacturacionController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private List<Map<String, Object>> filas(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query().listOfRows();
    }

    @GetMapping("/api/comprobantes")
    public List<Map<String, Object>> comprobantes() {
        return filas("""
                SELECT id, venta_id AS \"ventaId\", local_id AS \"localId\", tipo, serie,
                       correlativo, monto_total AS \"montoTotal\",
                       fecha_emision AS \"fechaEmision\",
                       estado_sunat AS \"estadoSunat\"
                  FROM comprobantes ORDER BY fecha_emision DESC LIMIT 200
                """);
    }

    @GetMapping("/api/devoluciones")
    public List<Map<String, Object>> devoluciones() {
        return filas("""
                SELECT d.id, d.venta_id AS \"ventaId\", d.local_id AS \"localId\",
                       d.usuario_id AS \"usuarioId\", d.motivo, d.fecha,
                       COALESCE(SUM(dd.monto_devuelto), 0) AS total
                  FROM devoluciones d
                  LEFT JOIN detalle_devoluciones dd ON dd.devolucion_id = d.id
                 GROUP BY d.id
                 ORDER BY d.fecha DESC LIMIT 200
                """);
    }

    @GetMapping("/api/devoluciones/{id}")
    public Map<String, Object> devolucionesPorId(@PathVariable UUID id) {
        var r = filas("""
                SELECT id, venta_id AS \"ventaId\", local_id AS \"localId\",
                       usuario_id AS \"usuarioId\", motivo, fecha
                  FROM devoluciones
                 WHERE id = ?
                 ORDER BY fecha DESC LIMIT 200
                """, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }

    @GetMapping("/api/notas-credito")
    public List<Map<String, Object>> notasCredito() {
        return filas("""
                SELECT id, venta_id AS \"ventaId\", comprobante_id AS \"comprobanteId\",
                       usuario_id AS \"usuarioId\", motivo,
                       monto_total AS \"montoTotal\", fecha
                  FROM notas_credito ORDER BY fecha DESC LIMIT 200
                """);
    }
}

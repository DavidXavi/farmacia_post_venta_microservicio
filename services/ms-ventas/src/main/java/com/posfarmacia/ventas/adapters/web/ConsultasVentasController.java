package com.posfarmacia.ventas.adapters.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de lectura de ms-ventas.
 *
 * <p>Devuelve mapas y no DTO tipados a propósito: son listados para pantallas de
 * administración, sin lógica y sin contrato entre servicios que proteger. Un record por
 * cada uno serían 1 clases que no aportan nada y que hay que cambiar cada vez que la
 * pantalla muestra una columna más. Los DTO tipados viven en {@code contracts} y son
 * los que SÍ cruzan servicios.
 *
 * <p>SQL explícito, igual que el resto de la persistencia del proyecto: son consultas
 * planas sobre tablas propias.
 */
@RestController
public class ConsultasVentasController {

    private final JdbcClient jdbc;

    public ConsultasVentasController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private List<Map<String, Object>> filas(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query().listOfRows();
    }

    /**
     * Efectivo cobrado en una sesion de caja. Lo pide ms-identidad al cerrar la caja para
     * calcular el arqueo: sin este dato el esperado era solo el monto inicial y cada cierre
     * mostraba como sobrante todo lo vendido en efectivo.
     *
     * <p>Solo ventas CONFIRMADA: una anulada devolvio el dinero y un borrador no se cobro.
     */
    @GetMapping("/api/ventas/sesiones-caja/{sesionId}/efectivo")
    public Map<String, Object> efectivoDeSesion(@PathVariable UUID sesionId) {
        BigDecimal efectivo = jdbc.sql("""
                SELECT COALESCE(SUM(p.monto), 0)
                  FROM pagos p
                  JOIN ventas v ON v.id = p.venta_id
                  JOIN formas_pago f ON f.id = p.forma_pago_id
                 WHERE v.sesion_caja_id = ? AND v.estado = 'CONFIRMADA' AND f.tipo = 'EFECTIVO'
                """).param(sesionId).query(BigDecimal.class).single();
        return Map.of("sesionId", sesionId, "efectivo", efectivo);
    }

    @GetMapping("/api/formas-pago")
    public List<Map<String, Object>> formasPago() {
        return filas("""
                SELECT id, nombre, tipo, activo FROM formas_pago WHERE activo = true ORDER BY nombre
                """);
    }

    /** Ventas recientes para el listado. El detalle completo lo sirve VentasController. */
    @GetMapping("/api/ventas")
    public List<Map<String, Object>> ventas(
            @RequestParam(required = false) UUID localId,
            @RequestParam(defaultValue = "50") int limite) {
        if (localId != null) {
            return filas("""
                    SELECT id, fecha, local_id AS "localId", caja_id AS "cajaId",
                           usuario_id AS "usuarioId", cliente_id AS "clienteId", estado,
                           numero_correlativo AS "numeroCorrelativo"
                      FROM ventas WHERE local_id = ? ORDER BY fecha DESC LIMIT ?
                    """, localId, limite);
        }
        return filas("""
                SELECT id, fecha, local_id AS "localId", caja_id AS "cajaId",
                       usuario_id AS "usuarioId", cliente_id AS "clienteId", estado,
                       numero_correlativo AS "numeroCorrelativo"
                  FROM ventas ORDER BY fecha DESC LIMIT ?
                """, limite);
    }
}

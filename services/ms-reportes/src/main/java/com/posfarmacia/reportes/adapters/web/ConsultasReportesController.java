package com.posfarmacia.reportes.adapters.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de lectura de ms-reportes.
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
public class ConsultasReportesController {

    private final JdbcClient jdbc;

    public ConsultasReportesController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private List<Map<String, Object>> filas(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query().listOfRows();
    }

    @GetMapping("/api/reglas-incentivo")
    public List<Map<String, Object>> reglas() {
        return filas("""
                SELECT id, nombre, producto_id AS \"productoId\", categoria_id AS \"categoriaId\",
                       monto_por_unidad AS \"montoPorUnidad\",
                       vigencia_inicio AS \"vigenciaInicio\",
                       vigencia_fin AS \"vigenciaFin\", activa
                  FROM reglas_incentivo ORDER BY nombre
                """);
    }

    @GetMapping("/api/reglas-incentivo/{id}")
    public Map<String, Object> reglasPorId(@PathVariable UUID id) {
        var r = filas("""
                SELECT id, nombre, producto_id AS \"productoId\", categoria_id AS \"categoriaId\",
                       monto_por_unidad AS \"montoPorUnidad\",
                       vigencia_inicio AS \"vigenciaInicio\",
                       vigencia_fin AS \"vigenciaFin\", activa
                  FROM reglas_incentivo
                 WHERE id = ?
                 ORDER BY nombre
                """, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }

    @GetMapping("/api/incentivos")
    public List<Map<String, Object>> incentivos() {
        return filas("""
                SELECT i.id, i.usuario_id AS \"usuarioId\", i.venta_id AS \"ventaId\",
                       i.cantidad, i.monto_calculado AS \"montoCalculado\", i.fecha,
                       r.nombre AS \"regla\"
                  FROM incentivos_venta i
                  JOIN reglas_incentivo r ON r.id = i.regla_incentivo_id
                 ORDER BY i.fecha DESC LIMIT 200
                """);
    }

    @GetMapping("/api/actividad-reciente")
    public List<Map<String, Object>> actividadReciente() {
        // Se arma desde las ventas ya proyectadas: cada fila es la prueba de que el
        // evento salio del outbox, paso por Kafka y llego hasta el read model.
        return filas("""
                SELECT fecha AS "ocurridoEn",
                       'Kafka: pos.ventas.confirmadas' AS "origen",
                       'VentaConfirmada' AS "tipo",
                       'Venta ' || left(venta_id::text, 8) || ' por S/ ' || total
                           || ' en ' || COALESCE(local_nombre, 'local ' || left(local_id::text, 8))
                           || ' (' || cantidad_lineas || ' linea(s))' AS "descripcion"
                  FROM rm_ventas
                 ORDER BY fecha DESC
                 LIMIT 50
                """);
    }

    // /api/reportes/ventas-diarias lo sirve ReportesController, que es donde vive
    // la consulta del read model. Duplicarlo aqui rompia el arranque.

    /**
     * Lotes por vencer.
     *
     * <p>Devuelve vacio: los lotes viven en pg_inventario y este servicio no puede
     * leer esa base, que es exactamente la regla del sistema. Para servirlo de verdad
     * hay que proyectarlos al read model desde el topico pos.stock.movimientos, igual
     * que se hace con las ventas. Queda pendiente y esta dicho, en vez de romper el
     * aislamiento con una consulta cruzada.
     */
    @GetMapping("/api/reportes/lotes-proximos-a-vencer")
    public List<Map<String, Object>> lotesPorVencer(
            @RequestParam(defaultValue = "90") int diasHorizonte) {
        return List.of();
    }
}

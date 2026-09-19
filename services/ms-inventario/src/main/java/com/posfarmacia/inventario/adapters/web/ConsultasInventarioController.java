package com.posfarmacia.inventario.adapters.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de lectura de ms-inventario.
 *
 * <p>Devuelve mapas y no DTO tipados a propósito: son listados para pantallas de
 * administración, sin lógica y sin contrato entre servicios que proteger. Un record por
 * cada uno serían 2 clases que no aportan nada y que hay que cambiar cada vez que la
 * pantalla muestra una columna más. Los DTO tipados viven en {@code contracts} y son
 * los que SÍ cruzan servicios.
 *
 * <p>SQL explícito, igual que el resto de la persistencia del proyecto: son consultas
 * planas sobre tablas propias.
 */
@RestController
public class ConsultasInventarioController {

    private final JdbcClient jdbc;

    public ConsultasInventarioController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private List<Map<String, Object>> filas(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query().listOfRows();
    }

    @GetMapping("/api/lotes")
    public List<Map<String, Object>> lotes() {
        return filas("""
                SELECT id, codigo, producto_id AS \"productoId\", local_id AS \"localId\",
                       fecha_vencimiento AS \"fechaVencimiento\",
                       cantidad_recibida AS \"cantidadRecibida\",
                       cantidad_disponible AS \"cantidadDisponible\", costo, estado
                  FROM lotes ORDER BY fecha_vencimiento
                """);
    }

    @GetMapping("/api/lotes/{id}")
    public Map<String, Object> lotesPorId(@PathVariable UUID id) {
        var r = filas("""
                SELECT id, codigo, producto_id AS \"productoId\", local_id AS \"localId\",
                       fecha_vencimiento AS \"fechaVencimiento\",
                       cantidad_recibida AS \"cantidadRecibida\",
                       cantidad_disponible AS \"cantidadDisponible\", costo, estado
                  FROM lotes
                 WHERE id = ?
                 ORDER BY fecha_vencimiento
                """, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }

    @GetMapping("/api/inventarios")
    public List<Map<String, Object>> existencias(@RequestParam(required = false) UUID localId) {
        if (localId != null) {
            return filas("""
                    SELECT producto_id AS "productoId", local_id AS "localId",
                           disponible, reservado, (disponible - reservado) AS "libre",
                           disponible AS "cantidadActual",
                           actualizado_en AS "actualizadoEn"
                      FROM stock_local WHERE local_id = ? ORDER BY disponible DESC
                    """, localId);
        }
        return filas("""
                SELECT producto_id AS "productoId", local_id AS "localId",
                       disponible, reservado, (disponible - reservado) AS "libre",
                       disponible AS "cantidadActual",
                       actualizado_en AS "actualizadoEn"
                  FROM stock_local ORDER BY disponible DESC LIMIT 500
                """);
    }
}

package com.posfarmacia.catalogo.adapters.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de lectura de ms-catalogo.
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
public class ConsultasCatalogoController {

    private final JdbcClient jdbc;

    public ConsultasCatalogoController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private List<Map<String, Object>> filas(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query().listOfRows();
    }

    @GetMapping("/api/categorias")
    public List<Map<String, Object>> categorias() {
        return filas("""
                SELECT id, nombre FROM categorias ORDER BY nombre
                """);
    }

    @GetMapping("/api/categorias/{id}")
    public Map<String, Object> categoriasPorId(@PathVariable UUID id) {
        var r = filas("""
                SELECT id, nombre FROM categorias WHERE id = ?
                """, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }

    @GetMapping("/api/laboratorios")
    public List<Map<String, Object>> laboratorios() {
        return filas("""
                SELECT id, nombre FROM laboratorios ORDER BY nombre
                """);
    }

    @GetMapping("/api/laboratorios/{id}")
    public Map<String, Object> laboratoriosPorId(@PathVariable UUID id) {
        var r = filas("""
                SELECT id, nombre FROM laboratorios WHERE id = ?
                """, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }

    @GetMapping("/api/presentaciones")
    public List<Map<String, Object>> presentaciones() {
        return filas("""
                SELECT id, nombre, unidad_medida AS \"unidadMedida\" FROM presentaciones ORDER BY nombre
                """);
    }

    @GetMapping("/api/presentaciones/{id}")
    public Map<String, Object> presentacionesPorId(@PathVariable UUID id) {
        var r = filas("""
                SELECT id, nombre, unidad_medida AS \"unidadMedida\" FROM presentaciones WHERE id = ?
                """, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }
}

package com.posfarmacia.clientes.adapters.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de lectura de ms-clientes.
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
public class ConsultasClientesController {

    private final JdbcClient jdbc;

    public ConsultasClientesController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private List<Map<String, Object>> filas(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query().listOfRows();
    }

    @GetMapping("/api/clientes")
    public List<Map<String, Object>> clientes() {
        return filas("""
                SELECT id, dni, nombres, apellidos, telefono, correo, direccion, estado
                  FROM clientes ORDER BY apellidos, nombres
                """);
    }

    // GET /api/clientes/{id} lo sirve ClientesController, que ademas devuelve los
    // convenios vigentes del cliente. Duplicarlo aqui rompia el arranque con
    // "Ambiguous mapping".

    @GetMapping("/api/convenios")
    public List<Map<String, Object>> convenios() {
        return filas("""
                SELECT id, nombre, activo FROM convenios_seguro ORDER BY nombre
                """);
    }

    @GetMapping("/api/convenios/{id}")
    public Map<String, Object> conveniosPorId(@PathVariable UUID id) {
        var r = filas("""
                SELECT id, nombre, activo FROM convenios_seguro
                 WHERE id = ?
                 ORDER BY nombre
                """, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }

    @GetMapping("/api/recetas")
    public List<Map<String, Object>> recetas() {
        return filas("""
                SELECT id, numero, tipo, fecha_emision AS \"fechaEmision\",
                       fecha_vencimiento AS \"fechaVencimiento\",
                       producto_id AS \"productoId\", cliente_id AS \"clienteId\",
                       datos_paciente AS \"datosPaciente\",
                       datos_profesional AS \"datosProfesional\",
                       cantidad_autorizada AS \"cantidadAutorizada\", estado
                  FROM recetas ORDER BY fecha_emision DESC
                """);
    }

    @GetMapping("/api/recetas/{id}")
    public Map<String, Object> recetasPorId(@PathVariable UUID id) {
        var r = filas("""
                SELECT id, numero, tipo, fecha_emision AS \"fechaEmision\",
                       fecha_vencimiento AS \"fechaVencimiento\",
                       producto_id AS \"productoId\", cliente_id AS \"clienteId\",
                       datos_paciente AS \"datosPaciente\",
                       datos_profesional AS \"datosProfesional\",
                       cantidad_autorizada AS \"cantidadAutorizada\", estado
                  FROM recetas
                 WHERE id = ?
                 ORDER BY fecha_emision DESC
                """, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }
}

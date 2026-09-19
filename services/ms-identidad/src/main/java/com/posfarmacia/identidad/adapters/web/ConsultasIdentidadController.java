package com.posfarmacia.identidad.adapters.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de lectura de ms-identidad.
 *
 * <p>Devuelve mapas y no DTO tipados a propósito: son listados para pantallas de
 * administración, sin lógica y sin contrato entre servicios que proteger. Un record por
 * cada uno serían 5 clases que no aportan nada y que hay que cambiar cada vez que la
 * pantalla muestra una columna más. Los DTO tipados viven en {@code contracts} y son
 * los que SÍ cruzan servicios.
 *
 * <p>SQL explícito, igual que el resto de la persistencia del proyecto: son consultas
 * planas sobre tablas propias.
 */
@RestController
public class ConsultasIdentidadController {

    private final JdbcClient jdbc;

    public ConsultasIdentidadController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private List<Map<String, Object>> filas(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query().listOfRows();
    }

    @GetMapping("/api/locales")
    public List<Map<String, Object>> locales() {
        return filas("""
                SELECT id, nombre, direccion, activo FROM locales ORDER BY nombre
                """);
    }

    @GetMapping("/api/locales/{id}")
    public Map<String, Object> localesPorId(@PathVariable UUID id) {
        var r = filas("""
                SELECT id, nombre, direccion, activo FROM locales
                 WHERE id = ?
                 ORDER BY nombre
                """, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }

    @GetMapping("/api/cajas")
    public List<Map<String, Object>> cajas() {
        return filas("""
                SELECT c.id, c.nombre, c.local_id AS \"localId\", c.activa,
                       l.nombre AS \"localNombre\"
                  FROM cajas c JOIN locales l ON l.id = c.local_id
                 ORDER BY l.nombre, c.nombre
                """);
    }

    @GetMapping("/api/cajas/{id}")
    public Map<String, Object> cajasPorId(@PathVariable UUID id) {
        var r = filas("""
                SELECT c.id, c.nombre, c.local_id AS \"localId\", c.activa,
                       l.nombre AS \"localNombre\"
                  FROM cajas c JOIN locales l ON l.id = c.local_id
                 WHERE c.id = ?
                """, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }

    @GetMapping("/api/usuarios")
    public List<Map<String, Object>> usuarios() {
        var filas = filas("""
                SELECT u.id, u.nombre_usuario AS "nombreUsuario", u.estado, u.email,
                       u.local_id AS "localId", u.mfa_habilitado AS "mfaHabilitado",
                       COALESCE(string_agg(r.nombre, ','), '') AS roles
                  FROM usuarios u
                  LEFT JOIN usuarios_roles ur ON ur.usuario_id = u.id
                  LEFT JOIN roles r ON r.id = ur.rol_id
                 GROUP BY u.id
                 ORDER BY u.nombre_usuario
                """);

        // La pantalla hace roles.join(): necesita un arreglo. Se parte aqui en vez de
        // devolver un array de Postgres, que el driver entrega como java.sql.Array y
        // Jackson no serializa de forma util.
        for (var fila : filas) {
            String crudo = String.valueOf(fila.getOrDefault("roles", ""));
            fila.put("roles", crudo.isBlank() ? List.of() : List.of(crudo.split(",")));
        }
        return filas;
    }

    @GetMapping("/api/roles")
    public List<Map<String, Object>> roles() {
        return filas("""
                SELECT id, nombre, descripcion FROM roles ORDER BY nombre
                """);
    }

    @GetMapping("/api/auditoria")
    public List<Map<String, Object>> auditoria() {
        return filas("""
                SELECT id, fecha, usuario_id AS \"usuarioId\", servicio, accion, entidad,
                       entidad_id AS \"entidadId\", detalle, trace_id AS \"traceId\"
                  FROM auditoria_operaciones ORDER BY fecha DESC LIMIT 200
                """);
    }
}

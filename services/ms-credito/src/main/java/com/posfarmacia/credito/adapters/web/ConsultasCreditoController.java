package com.posfarmacia.credito.adapters.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de lectura de ms-credito.
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
public class ConsultasCreditoController {

    private final JdbcClient jdbc;

    public ConsultasCreditoController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private List<Map<String, Object>> filas(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query().listOfRows();
    }

    @GetMapping("/api/lineas-credito")
    public List<Map<String, Object>> lineas() {
        return filas("""
                SELECT id, cliente_id AS \"clienteId\",
                       monto_autorizado AS \"montoAutorizado\",
                       saldo_disponible AS \"saldoDisponible\", reservado,
                       vigencia_inicio AS \"vigenciaInicio\",
                       vigencia_fin AS \"vigenciaFin\", estado
                  FROM lineas_credito ORDER BY monto_autorizado DESC
                """);
    }

    @GetMapping("/api/lineas-credito/{id}")
    public Map<String, Object> lineasPorId(@PathVariable UUID id) {
        var r = filas("""
                SELECT id, cliente_id AS \"clienteId\",
                       monto_autorizado AS \"montoAutorizado\",
                       saldo_disponible AS \"saldoDisponible\", reservado,
                       vigencia_inicio AS \"vigenciaInicio\",
                       vigencia_fin AS \"vigenciaFin\", estado
                  FROM lineas_credito
                 WHERE id = ?
                 ORDER BY monto_autorizado DESC
                """, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }
}

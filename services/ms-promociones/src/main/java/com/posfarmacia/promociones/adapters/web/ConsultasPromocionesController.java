package com.posfarmacia.promociones.adapters.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de lectura de ms-promociones.
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
public class ConsultasPromocionesController {

    private final JdbcClient jdbc;

    public ConsultasPromocionesController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private List<Map<String, Object>> filas(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query().listOfRows();
    }

    @GetMapping("/api/promociones")
    public List<Map<String, Object>> promociones() {
        return filas("""
                SELECT p.id, p.nombre, p.descripcion,
                       p.tipo_beneficio AS \"tipoBeneficio\",
                       p.valor_beneficio AS \"valorBeneficio\",
                       p.requiere_cliente AS \"requiereCliente\",
                       p.cantidad_minima AS \"cantidadMinima\",
                       p.vigencia_inicio AS \"vigenciaInicio\",
                       p.vigencia_fin AS \"vigenciaFin\", p.activa,
                       COUNT(c.id) AS \"productosAlcanzados\"
                  FROM promociones p
                  LEFT JOIN promocion_condiciones c ON c.promocion_id = p.id
                 GROUP BY p.id
                 ORDER BY p.activa DESC, p.nombre
                """);
    }
}

package com.posfarmacia.reportes.adapters.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta de reglas de incentivo al vendedor.
 *
 * <p>Sin caso de uso ni puerto: es un INSERT con dos validaciones. La unica regla que
 * importa es que la regla apunte a algo, producto o categoria: una sin ninguno de los
 * dos alcanza a todas las ventas y multiplica la comision de golpe sin que nadie lo
 * haya pedido.
 */
@RestController
public class ReglasIncentivoController {

    public record ReglaPeticion(
            @NotBlank String nombre,
            UUID productoId,
            UUID categoriaId,
            @NotNull @Positive BigDecimal montoPorUnidad,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFin) {
    }

    private final JdbcClient jdbc;

    public ReglasIncentivoController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/api/reglas-incentivo")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR_CENTRAL')")
    public ResponseEntity<Map<String, UUID>> crear(@Valid @RequestBody ReglaPeticion p) {
        if (p.productoId() == null && p.categoriaId() == null) {
            throw new IllegalArgumentException(
                    "La regla tiene que apuntar a un producto o a una categoria: "
                            + "sin ninguno de los dos alcanzaria a todas las ventas");
        }
        if (p.vigenciaInicio() != null && p.vigenciaFin() != null
                && p.vigenciaFin().isBefore(p.vigenciaInicio())) {
            throw new IllegalArgumentException("La vigencia termina antes de empezar");
        }

        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reglas_incentivo (id, nombre, producto_id, categoria_id,
                                                      monto_por_unidad, vigencia_inicio,
                                                      vigencia_fin, activa)
                        VALUES (?, ?, ?, ?, ?, ?, ?, true)
                        """)
                .param(id).param(p.nombre().trim()).param(p.productoId()).param(p.categoriaId())
                .param(p.montoPorUnidad()).param(p.vigenciaInicio()).param(p.vigenciaFin())
                .update();
        return ResponseEntity.status(201).body(Map.of("id", id));
    }
}

package com.posfarmacia.identidad.adapters.web;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apertura y cierre de caja.
 *
 * <p>Vive en ms-identidad y no en ms-ventas porque la sesión de caja es de quién
 * atiende, no de qué se vende: pertenece al mismo agregado que el usuario, el local y
 * la caja física. ms-ventas solo guarda el id de la sesión en la venta, como referencia.
 *
 * <p>Sin una sesión abierta no se puede vender, y eso es deliberado: el arqueo de fin
 * de turno necesita saber contra qué apertura se compara el efectivo contado.
 */
@RestController
@RequestMapping("/api/cajas")
public class SesionesCajaController {

    public record Apertura(UUID usuarioId, BigDecimal montoInicial) {
    }

    public record Cierre(BigDecimal montoDeclarado, String observacion) {
    }

    private static final String ACTIVA = """
            SELECT id, caja_id AS "cajaId", usuario_id AS "usuarioId",
                   fecha_apertura AS "fechaApertura", monto_inicial AS "montoInicial",
                   estado
              FROM sesiones_caja
             WHERE caja_id = ? AND estado = 'ABIERTA'
             ORDER BY fecha_apertura DESC LIMIT 1
            """;

    private final JdbcClient jdbc;

    public SesionesCajaController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{cajaId}/sesion-activa")
    public ResponseEntity<Map<String, Object>> sesionActiva(@PathVariable UUID cajaId) {
        var filas = jdbc.sql(ACTIVA).param(cajaId).query().listOfRows();
        // 404 y no un objeto vacío: la pantalla distingue "no hay sesión abierta" de
        // "hay una", y con un 200 vacío mostraría una caja abierta que no lo está.
        return filas.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(filas.get(0));
    }

    @PostMapping("/{cajaId}/aperturas")
    @Transactional
    public ResponseEntity<Map<String, Object>> abrir(@PathVariable UUID cajaId,
            @Valid @RequestBody Apertura p) {
        // Dos sesiones abiertas en la misma caja harían imposible cuadrar el arqueo.
        var yaAbierta = jdbc.sql(ACTIVA).param(cajaId).query().listOfRows();
        if (!yaAbierta.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "La caja ya tiene una sesión abierta",
                    "sesionId", yaAbierta.get(0).get("id")));
        }

        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO sesiones_caja (id, caja_id, usuario_id, fecha_apertura,
                                           monto_inicial, estado)
                VALUES (?, ?, ?, now(), ?, 'ABIERTA')
                """)
                .param(id).param(cajaId).param(p.usuarioId())
                .param(p.montoInicial() == null ? BigDecimal.ZERO : p.montoInicial())
                .update();

        return ResponseEntity.ok(jdbc.sql(ACTIVA).param(cajaId).query().listOfRows().get(0));
    }

    @PostMapping("/{cajaId}/cierres")
    @Transactional
    public ResponseEntity<Map<String, Object>> cerrar(@PathVariable UUID cajaId,
            @Valid @RequestBody Cierre p) {
        var abiertas = jdbc.sql(ACTIVA).param(cajaId).query().listOfRows();
        if (abiertas.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of("error", "La caja no tiene sesión abierta"));
        }
        UUID sesionId = (UUID) abiertas.get(0).get("id");

        // La diferencia se calcula y se guarda: es el dato del arqueo, y dejarlo para
        // que lo calcule cada reporte es como dos reportes terminan sin cuadrar.
        jdbc.sql("""
                UPDATE sesiones_caja
                   SET fecha_cierre = now(),
                       monto_declarado = ?,
                       monto_esperado = monto_inicial,
                       diferencia = ? - monto_inicial,
                       observacion_cierre = ?,
                       estado = 'CERRADA'
                 WHERE id = ?
                """)
                .param(p.montoDeclarado()).param(p.montoDeclarado())
                .param(p.observacion()).param(sesionId)
                .update();

        var cerrada = jdbc.sql("""
                SELECT id, caja_id AS "cajaId", usuario_id AS "usuarioId",
                       fecha_apertura AS "fechaApertura", fecha_cierre AS "fechaCierre",
                       monto_inicial AS "montoInicial", monto_declarado AS "montoDeclarado",
                       monto_esperado AS "montoEsperado", diferencia,
                       observacion_cierre AS "observacionCierre", estado
                  FROM sesiones_caja WHERE id = ?
                """).param(sesionId).query().listOfRows().get(0);
        return ResponseEntity.ok(cerrada);
    }
}

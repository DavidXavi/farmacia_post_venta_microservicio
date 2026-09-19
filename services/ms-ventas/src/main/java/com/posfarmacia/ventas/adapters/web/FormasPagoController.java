package com.posfarmacia.ventas.adapters.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta de formas de pago.
 *
 * <p>Sin caso de uso ni puerto: es un INSERT en una tabla de catalogo de ocho filas,
 * sin reglas de negocio mas alla de que el tipo sea uno de los conocidos. Un puerto,
 * un adaptador y un servicio para esto serian tres archivos que no deciden nada.
 *
 * <p>El tipo si se valida contra la lista cerrada: la caja decide como cobrar segun el
 * tipo, y uno inventado hace que la forma de pago aparezca en la pantalla y no cobre.
 */
@RestController
public class FormasPagoController {

    private static final Set<String> TIPOS = Set.of(
            "EFECTIVO", "TARJETA_DEBITO", "TARJETA_CREDITO", "TRANSFERENCIA",
            "BILLETERA_DIGITAL", "COPAGO_SEGURO", "CREDITO_FARMACIA", "OTRO");

    public record FormaPagoPeticion(@NotBlank String nombre, @NotBlank String tipo) {
    }

    private final JdbcClient jdbc;

    public FormasPagoController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/api/formas-pago")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR_CENTRAL')")
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody FormaPagoPeticion p) {
        String tipo = p.tipo().trim().toUpperCase();
        if (!TIPOS.contains(tipo)) {
            throw new IllegalArgumentException(
                    "Tipo de forma de pago desconocido: " + tipo + ". Los validos son " + TIPOS);
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO formas_pago (id, nombre, tipo, activo) VALUES (?, ?, ?, true)")
                .param(id).param(p.nombre().trim()).param(tipo)
                .update();
        return ResponseEntity.status(201).body(Map.of(
                "id", id, "nombre", p.nombre(), "tipo", tipo, "activo", true));
    }
}

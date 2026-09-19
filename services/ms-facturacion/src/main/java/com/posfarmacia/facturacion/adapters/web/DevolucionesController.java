package com.posfarmacia.facturacion.adapters.web;

import com.posfarmacia.facturacion.usecases.usecase.RegistrarDevolucionUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registrar una devolucion.
 *
 * <p>Exige {@code Idempotency-Key}, igual que confirmar una venta y por la misma
 * razon: un doble clic no puede devolver el dinero dos veces. Aqui es peor que en una
 * venta, porque el dinero sale y no entra.
 *
 * <p>El usuario sale del token. La devolucion queda firmada por quien la hizo, no por
 * quien el navegador diga.
 */
@RestController
public class DevolucionesController {

    public record LineaPeticion(@NotNull UUID detalleVentaId, int cantidad) {
    }

    public record DevolucionPeticion(@NotNull UUID ventaId, String motivo,
                                     @NotEmpty List<LineaPeticion> lineas) {
    }

    private final RegistrarDevolucionUseCase devoluciones;

    public DevolucionesController(RegistrarDevolucionUseCase devoluciones) {
        this.devoluciones = devoluciones;
    }

    @PostMapping("/api/devoluciones")
    public ResponseEntity<Map<String, Object>> registrar(
            @RequestHeader(value = "Idempotency-Key", required = false) String clave,
            @Valid @RequestBody DevolucionPeticion p,
            @AuthenticationPrincipal Jwt jwt) {
        var resultado = devoluciones.registrar(p.ventaId(),
                UUID.fromString(jwt.getSubject()), p.motivo(),
                p.lineas().stream()
                        .map(l -> new RegistrarDevolucionUseCase.LineaPedida(
                                l.detalleVentaId(), l.cantidad()))
                        .toList());
        return ResponseEntity.status(201).body(Map.of(
                "id", resultado.devolucionId(),
                "notaCreditoId", resultado.notaCreditoId(),
                "montoTotal", resultado.montoTotal()));
    }
}

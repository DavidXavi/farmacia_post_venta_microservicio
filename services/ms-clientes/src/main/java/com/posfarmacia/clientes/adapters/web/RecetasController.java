package com.posfarmacia.clientes.adapters.web;

import com.posfarmacia.clientes.usecases.usecase.GestionarRecetasUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta de recetas y validacion del quimico farmaceutico.
 *
 * <p>La validacion exige el rol QUIMICO_FARMACEUTICO. Es el unico permiso del servicio
 * que de verdad separa responsabilidades: cargar el papel lo puede hacer cualquiera
 * que atienda, aprobarlo no.
 *
 * <p>El validador sale del token. Si viniera en el cuerpo, la firma de la aprobacion
 * seria la que el cliente quiera poner, y la auditoria de controlados no valdria nada.
 */
@RestController
public class RecetasController {

    public record RecetaPeticion(
            @NotBlank String numero,
            String tipo,
            @NotNull LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            @NotNull UUID productoId,
            UUID clienteId,
            @NotBlank String datosPaciente,
            @NotBlank String datosProfesional,
            String dosis,
            @Positive int cantidadAutorizada,
            String archivoRespaldoUrl) {
    }

    public record ValidacionPeticion(@NotNull UUID recetaId, boolean aprobar,
                                     String observaciones) {
    }

    private final GestionarRecetasUseCase recetas;

    public RecetasController(GestionarRecetasUseCase recetas) {
        this.recetas = recetas;
    }

    @PostMapping("/api/recetas")
    public ResponseEntity<Map<String, Object>> registrar(@Valid @RequestBody RecetaPeticion p) {
        UUID id = recetas.registrar(new GestionarRecetasUseCase.AltaReceta(
                p.numero(), p.tipo(), p.fechaEmision(), p.fechaVencimiento(),
                p.productoId(), p.clienteId(), p.datosPaciente(), p.datosProfesional(),
                p.dosis(), p.cantidadAutorizada(), p.archivoRespaldoUrl()));
        return ResponseEntity.status(201).body(Map.of("id", id, "estado", "PENDIENTE"));
    }

    @PostMapping("/api/recetas/validaciones")
    @PreAuthorize("hasAnyRole('QUIMICO_FARMACEUTICO', 'ADMINISTRADOR')")
    public ResponseEntity<Map<String, Object>> validar(@Valid @RequestBody ValidacionPeticion p,
            @AuthenticationPrincipal Jwt jwt) {
        String estado = recetas.validar(p.recetaId(), UUID.fromString(jwt.getSubject()),
                p.aprobar(), p.observaciones());
        return ResponseEntity.ok(Map.of("recetaId", p.recetaId(), "estado", estado));
    }
}

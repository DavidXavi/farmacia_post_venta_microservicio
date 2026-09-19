package com.posfarmacia.catalogo.adapters.web;

import com.posfarmacia.catalogo.usecases.usecase.RegistrarCatalogoUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Altas del catalogo.
 *
 * <p>Va aparte de {@link ConsultasCatalogoController} porque son dos publicos
 * distintos: las consultas las llama el POS 5000 veces por segundo y son anonimas
 * dentro del sistema; las altas las hace un administrador un punado de veces al dia y
 * exigen rol. Juntarlas obligaria a razonar sobre permisos en cada metodo de lectura.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR_CENTRAL')")
public class AltasCatalogoController {

    public record NombrePeticion(@NotBlank String nombre) {
    }

    public record PresentacionPeticion(@NotBlank String nombre, @NotBlank String unidadMedida) {
    }

    public record ProductoPeticion(
            @NotBlank String codigoInterno,
            String codigoBarras,
            @NotBlank String nombreComercial,
            String descripcion,
            @NotBlank String tipoProducto,
            @NotNull UUID categoriaId,
            @NotNull UUID laboratorioId,
            @NotNull UUID presentacionId,
            @NotNull @Positive BigDecimal precioVenta,
            boolean esControlado,
            boolean requiereReceta,
            String tipoRecetaRequerida) {
    }

    private final RegistrarCatalogoUseCase registrar;

    public AltasCatalogoController(RegistrarCatalogoUseCase registrar) {
        this.registrar = registrar;
    }

    @PostMapping("/api/productos")
    public ResponseEntity<Map<String, UUID>> producto(@Valid @RequestBody ProductoPeticion p) {
        UUID id = registrar.registrarProducto(new RegistrarCatalogoUseCase.AltaProducto(
                p.codigoInterno(), p.codigoBarras(), p.nombreComercial(), p.descripcion(),
                p.tipoProducto(), p.categoriaId(), p.laboratorioId(), p.presentacionId(),
                p.precioVenta(), p.esControlado(), p.requiereReceta(), p.tipoRecetaRequerida()));
        return creado(id);
    }

    @PostMapping("/api/categorias")
    public ResponseEntity<Map<String, UUID>> categoria(@Valid @RequestBody NombrePeticion p) {
        return creado(registrar.registrarCategoria(p.nombre()));
    }

    @PostMapping("/api/laboratorios")
    public ResponseEntity<Map<String, UUID>> laboratorio(@Valid @RequestBody NombrePeticion p) {
        return creado(registrar.registrarLaboratorio(p.nombre()));
    }

    @PostMapping("/api/presentaciones")
    public ResponseEntity<Map<String, UUID>> presentacion(@Valid @RequestBody PresentacionPeticion p) {
        return creado(registrar.registrarPresentacion(p.nombre(), p.unidadMedida()));
    }

    private ResponseEntity<Map<String, UUID>> creado(UUID id) {
        return ResponseEntity.status(201).body(Map.of("id", id));
    }
}

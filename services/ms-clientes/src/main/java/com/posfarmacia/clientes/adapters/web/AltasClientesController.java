package com.posfarmacia.clientes.adapters.web;

import com.posfarmacia.clientes.usecases.usecase.RegistrarClientesUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Altas de clientes, convenios, coberturas y afiliaciones.
 *
 * <p>Sin {@code @PreAuthorize} por rol: registrar al cliente que esta en el mostrador
 * es parte de atender, y exigir un administrador para eso obligaria al cajero a
 * llamar a alguien con el cliente esperando. La autenticacion si es obligatoria, la
 * impone la cadena de seguridad compartida.
 */
@RestController
public class AltasClientesController {

    public record ClientePeticion(
            @NotBlank String dni,
            @NotBlank String nombres,
            @NotBlank String apellidos,
            LocalDate fechaNacimiento,
            String telefono,
            String correo,
            String direccion) {
    }

    public record CambioClientePeticion(String nombres, String apellidos, String telefono,
                                        String correo, String direccion, String estado) {
    }

    public record ConvenioPeticion(@NotBlank String nombre) {
    }

    public record CoberturaPeticion(@NotNull UUID productoId,
                                    @NotNull BigDecimal porcentajeCubierto) {
    }

    public record AfiliacionPeticion(@NotNull UUID clienteId, @NotNull UUID convenioId,
                                     LocalDate vigenciaInicio, LocalDate vigenciaFin) {
    }

    private final RegistrarClientesUseCase registrar;

    public AltasClientesController(RegistrarClientesUseCase registrar) {
        this.registrar = registrar;
    }

    @PostMapping("/api/clientes")
    public ResponseEntity<Map<String, Object>> cliente(@Valid @RequestBody ClientePeticion p) {
        UUID id = registrar.registrarCliente(new RegistrarClientesUseCase.AltaCliente(
                p.dni(), p.nombres(), p.apellidos(), p.fechaNacimiento(),
                p.telefono(), p.correo(), p.direccion()));
        // Devuelve el cliente armado y no solo el id: la pantalla lo muestra en cuanto
        // se guarda, y pedirlo otra vez seria un viaje de red para datos que ya tenemos.
        return ResponseEntity.status(201).body(Map.of(
                "id", id, "dni", p.dni(),
                "nombres", p.nombres(), "apellidos", p.apellidos(), "estado", "ACTIVO"));
    }

    @PatchMapping("/api/clientes/{id}")
    public ResponseEntity<Void> editar(@PathVariable UUID id,
            @RequestBody CambioClientePeticion p) {
        registrar.actualizarCliente(id, new RegistrarClientesUseCase.CambioCliente(
                p.nombres(), p.apellidos(), p.telefono(), p.correo(), p.direccion(), p.estado()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/convenios")
    public ResponseEntity<Map<String, UUID>> convenio(@Valid @RequestBody ConvenioPeticion p) {
        return ResponseEntity.status(201).body(Map.of("id", registrar.registrarConvenio(p.nombre())));
    }

    @PostMapping("/api/convenios/{convenioId}/coberturas")
    public ResponseEntity<Void> cobertura(@PathVariable UUID convenioId,
            @Valid @RequestBody CoberturaPeticion p) {
        registrar.guardarCobertura(convenioId, p.productoId(), p.porcentajeCubierto());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/convenios/afiliaciones")
    public ResponseEntity<Void> afiliacion(@Valid @RequestBody AfiliacionPeticion p) {
        registrar.afiliar(p.clienteId(), p.convenioId(), p.vigenciaInicio(), p.vigenciaFin());
        return ResponseEntity.noContent().build();
    }
}

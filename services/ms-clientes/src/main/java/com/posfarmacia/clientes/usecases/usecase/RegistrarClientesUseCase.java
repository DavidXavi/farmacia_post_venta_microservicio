package com.posfarmacia.clientes.usecases.usecase;

import com.posfarmacia.clientes.usecases.port.out.ClientesEscrituraPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clientes, convenios, coberturas y afiliaciones.
 *
 * <p>Las cuatro viven juntas porque son el mismo agregado extendido: un cliente sin su
 * convenio no sirve para cobrar, y un convenio sin afiliados no cubre a nadie. Es la
 * misma razon por la que comparten base.
 *
 * <p>Las recetas van aparte, en {@link GestionarRecetasUseCase}: tienen su propio ciclo
 * de vida (pendiente, aprobada, utilizada) y su propio responsable, el quimico
 * farmaceutico.
 */
@Service
public class RegistrarClientesUseCase {

    private final ClientesEscrituraPort escritura;

    public RegistrarClientesUseCase(ClientesEscrituraPort escritura) {
        this.escritura = escritura;
    }

    public record AltaCliente(String dni, String nombres, String apellidos,
                              LocalDate fechaNacimiento, String telefono, String correo,
                              String direccion) {
    }

    public record CambioCliente(String nombres, String apellidos, String telefono,
                                String correo, String direccion, String estado) {
    }

    @Transactional
    public UUID registrarCliente(AltaCliente alta) {
        String dni = exigirDni(alta.dni());
        if (escritura.existeDni(dni)) {
            throw new IllegalStateException("Ya hay un cliente registrado con el DNI " + dni);
        }
        UUID id = UUID.randomUUID();
        escritura.insertarCliente(new ClientesEscrituraPort.NuevoCliente(id, dni,
                exigir(alta.nombres(), "nombres"), exigir(alta.apellidos(), "apellidos"),
                alta.fechaNacimiento(), limpiar(alta.telefono()), limpiar(alta.correo()),
                limpiar(alta.direccion())));
        return id;
    }

    @Transactional
    public void actualizarCliente(UUID id, CambioCliente cambio) {
        boolean actualizado = escritura.actualizarCliente(id,
                new ClientesEscrituraPort.CambioCliente(
                        limpiar(cambio.nombres()), limpiar(cambio.apellidos()),
                        limpiar(cambio.telefono()), limpiar(cambio.correo()),
                        limpiar(cambio.direccion()), limpiar(cambio.estado())));
        if (!actualizado) {
            throw new IllegalArgumentException("No existe el cliente " + id);
        }
    }

    @Transactional
    public UUID registrarConvenio(String nombre) {
        UUID id = UUID.randomUUID();
        escritura.insertarConvenio(id, exigir(nombre, "nombre del convenio"));
        return id;
    }

    @Transactional
    public void guardarCobertura(UUID convenioId, UUID productoId, BigDecimal porcentaje) {
        exigirConvenio(convenioId);
        if (porcentaje == null || porcentaje.signum() < 0
                || porcentaje.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "El porcentaje cubierto tiene que estar entre 0 y 100");
        }
        escritura.guardarCobertura(UUID.randomUUID(), convenioId, productoId, porcentaje);
    }

    @Transactional
    public void afiliar(UUID clienteId, UUID convenioId, LocalDate inicio, LocalDate fin) {
        if (!escritura.existeCliente(clienteId)) {
            throw new IllegalArgumentException("No existe el cliente " + clienteId);
        }
        exigirConvenio(convenioId);
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new IllegalArgumentException(
                    "La vigencia termina antes de empezar: " + inicio + " a " + fin);
        }
        escritura.guardarAfiliacion(UUID.randomUUID(), clienteId, convenioId, inicio, fin);
    }

    private void exigirConvenio(UUID convenioId) {
        if (!escritura.existeConvenio(convenioId)) {
            throw new IllegalArgumentException("No existe el convenio " + convenioId);
        }
    }

    /** Ocho digitos: es un DNI peruano, no un texto libre. */
    private String exigirDni(String dni) {
        String limpio = dni == null ? "" : dni.trim();
        if (!limpio.matches("\\d{8}")) {
            throw new IllegalArgumentException("El DNI tiene que ser de ocho digitos");
        }
        return limpio;
    }

    private String exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + campo);
        }
        return valor.trim();
    }

    private String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}

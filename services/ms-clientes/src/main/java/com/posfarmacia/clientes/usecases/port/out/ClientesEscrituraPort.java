package com.posfarmacia.clientes.usecases.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Altas y cambios de ms-clientes.
 *
 * <p>Aparte de {@link ClientePort}, que es la lectura que consume ms-ventas en cada
 * venta. Las escrituras las hace el personal de la botica unas pocas veces al dia y
 * ninguna esta en el camino caliente.
 */
public interface ClientesEscrituraPort {

    record NuevoCliente(UUID id, String dni, String nombres, String apellidos,
                        LocalDate fechaNacimiento, String telefono, String correo,
                        String direccion) {
    }

    record CambioCliente(String nombres, String apellidos, String telefono,
                         String correo, String direccion, String estado) {
    }

    record NuevaReceta(UUID id, String numero, String tipo, LocalDate fechaEmision,
                       LocalDate fechaVencimiento, UUID productoId, UUID clienteId,
                       String datosPaciente, String datosProfesional, String dosis,
                       int cantidadAutorizada, String archivoRespaldoUrl) {
    }

    // --- clientes ---

    boolean existeDni(String dni);

    boolean existeCliente(UUID id);

    void insertarCliente(NuevoCliente cliente);

    /** Devuelve false si el cliente no existe: quien llama decide si eso es un 404. */
    boolean actualizarCliente(UUID id, CambioCliente cambio);

    // --- convenios y coberturas ---

    boolean existeConvenio(UUID id);

    void insertarConvenio(UUID id, String nombre);

    /**
     * Alta o cambio de la cobertura de un producto. Es un upsert porque la pantalla
     * dice "Guardar cobertura", no "Crear": volver a configurar un porcentaje que ya
     * existia tiene que cambiarlo, no fallar con un error de clave duplicada.
     */
    void guardarCobertura(UUID id, UUID convenioId, UUID productoId, BigDecimal porcentaje);

    void guardarAfiliacion(UUID id, UUID clienteId, UUID convenioId,
                           LocalDate vigenciaInicio, LocalDate vigenciaFin);

    // --- recetas ---

    boolean existeNumeroReceta(String numero);

    void insertarReceta(NuevaReceta receta);

    Optional<String> estadoReceta(UUID recetaId);

    void cambiarEstadoReceta(UUID recetaId, String estado);
}

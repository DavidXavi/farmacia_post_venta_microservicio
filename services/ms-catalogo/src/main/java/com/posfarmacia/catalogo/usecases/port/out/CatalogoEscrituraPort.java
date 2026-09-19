package com.posfarmacia.catalogo.usecases.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Altas del catalogo.
 *
 * <p>Separado de {@link ProductoPort}, que es puro de lectura y sirve al camino
 * caliente: 5000 req/s de consulta contra un punado de altas al dia. Meterlas en la
 * misma interfaz obligaria a cualquier implementacion de solo lectura (una replica, un
 * doble de prueba) a declarar metodos de escritura que nunca va a ejecutar.
 */
public interface CatalogoEscrituraPort {

    /** Datos de alta de un producto. Se define aqui porque es el vocabulario del caso de uso. */
    record NuevoProducto(
            UUID id,
            String codigoInterno,
            String codigoBarras,
            String nombreComercial,
            String descripcion,
            String tipoProducto,
            UUID categoriaId,
            UUID laboratorioId,
            UUID presentacionId,
            BigDecimal precioVenta,
            boolean esControlado,
            boolean requiereReceta,
            String tipoRecetaRequerida) {
    }

    void insertarProducto(NuevoProducto producto);

    boolean existeCodigoInterno(String codigoInterno);

    boolean existeCategoria(UUID id);

    boolean existeLaboratorio(UUID id);

    boolean existePresentacion(UUID id);

    void insertarCategoria(UUID id, String nombre);

    void insertarLaboratorio(UUID id, String nombre);

    void insertarPresentacion(UUID id, String nombre, String unidadMedida);
}

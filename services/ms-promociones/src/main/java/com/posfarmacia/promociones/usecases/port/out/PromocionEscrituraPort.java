package com.posfarmacia.promociones.usecases.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Altas y cambios de promociones.
 *
 * <p>Aparte de {@link PromocionPort}, que es la consulta que corre en cada venta. Aqui
 * se carga una promocion al mes; alli se leen todas varias veces por segundo.
 */
public interface PromocionEscrituraPort {

    record DatosPromocion(String nombre, String descripcion, String tipoBeneficio,
                          BigDecimal valorBeneficio, boolean requiereCliente,
                          int cantidadMinima, LocalDate vigenciaInicio, LocalDate vigenciaFin) {
    }

    void insertar(UUID id, DatosPromocion datos, List<UUID> productos);

    /** Devuelve false si la promocion no existe. */
    boolean actualizar(UUID id, DatosPromocion datos, List<UUID> productos);

    /** Estado actual: se usa para no desactivar dos veces ni auditar de mas. */
    Optional<Boolean> estaActiva(UUID id);

    void desactivar(UUID id);
}

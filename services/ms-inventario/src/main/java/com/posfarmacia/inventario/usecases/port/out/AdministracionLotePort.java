package com.posfarmacia.inventario.usecases.port.out;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Altas y cambios de estado de lotes.
 *
 * <p>Aparte de {@link LotePort}, que sirve al camino caliente de la venta (FEFO,
 * salida y compensacion) y se ejecuta cientos de veces por segundo. Esto otro lo usa
 * el encargado de inventario cuando llega mercaderia o cuando hay que sacar un lote de
 * circulacion: otra frecuencia, otro rol y otras reglas.
 */
public interface AdministracionLotePort {

    record NuevoLote(UUID id, String codigo, UUID productoId, UUID localId,
                     LocalDate fechaVencimiento, int cantidadRecibida,
                     java.math.BigDecimal costo) {
    }

    record ResumenLote(UUID id, UUID productoId, UUID localId, int cantidadDisponible,
                       String estado) {
    }

    void insertar(NuevoLote lote);

    boolean existeCodigoEnLocal(String codigo, UUID localId);

    Optional<ResumenLote> porId(UUID loteId);

    void cambiarEstado(UUID loteId, String estado);

    /** Suma al contador del local. El lote entra y el producto queda disponible para vender. */
    void sumarStock(UUID productoId, UUID localId, int cantidad);

    /** Resta del contador. Lo que sale de circulacion deja de poder venderse. */
    void restarStock(UUID productoId, UUID localId, int cantidad);

    void registrarMovimiento(UUID loteId, UUID productoId, UUID localId, String tipo,
                             int cantidad, UUID usuarioId, String referencia);
}

package com.posfarmacia.ventas.usecases.port.out;

import com.posfarmacia.ventas.domain.EstadoSaga;
import java.util.UUID;

/**
 * Estado de la coordinacion entre servicios.
 *
 * <p>Existe para que una venta a medias sea visible. Sin esto, cuando un consumidor
 * falla la venta queda confirmada, el stock sin descontar y nadie lo sabe: el sistema
 * no tiene forma de contestar "cuantas ventas de hoy quedaron sin comprobante".
 */
public interface SagaPort {

    enum Paso {
        STOCK,
        CREDITO,
        COMPROBANTE
    }

    void abrir(UUID ventaId);

    void marcar(UUID ventaId, Paso paso);

    boolean completa(UUID ventaId);

    void cerrar(UUID ventaId, EstadoSaga estado);

    void compensar(UUID ventaId, String motivo);
}

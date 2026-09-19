package com.posfarmacia.inventario.adapters.persistence;

import com.posfarmacia.inventario.usecases.port.out.StockPort;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * El contador de stock, en SQL directo.
 *
 * <p>ponytail: JdbcClient y no JPA. Aca no hay grafo de objetos ni ciclo de vida que
 * mapear, hay un contador con un UPDATE condicional. Una entidad JPA con su
 * repositorio, su mapper y su version optimista serian cuarenta lineas para hacer
 * peor lo que una sentencia hace bien: el lock optimista de JPA reintenta en la
 * aplicacion, y a 200 ventas/s sobre los productos estrella eso es una tormenta de
 * reintentos justo donde no se puede.
 *
 * <p>La clave del diseno esta en {@link #intentarReservar}: UN solo statement atomico.
 * Sin SELECT previo, sin FOR UPDATE, sin transaccion larga, sin posibilidad de
 * deadlock. Postgres resuelve la carrera con el lock de fila que ya toma para el
 * UPDATE, y el WHERE decide si la operacion procede. Si devuelve 0 filas, no habia
 * stock: no hizo falta preguntarlo antes.
 */
@Repository
public class StockJdbcAdapter implements StockPort {

    /**
     * El corazon del asunto.
     *
     * <p>La contencion queda acotada a la fila (producto, local), que es el shard
     * natural del negocio: dos boticas distintas nunca se pelean la misma fila, asi
     * que 500 locales vendiendo el mismo paracetamol son 500 filas independientes,
     * no una cola.
     */
    private static final String RESERVAR = """
            UPDATE stock_local
               SET reservado = reservado + ?, actualizado_en = now()
             WHERE producto_id = ? AND local_id = ?
               AND disponible - reservado >= ?
            """;

    private static final String LIBERAR = """
            UPDATE stock_local
               SET reservado = GREATEST(reservado - ?, 0), actualizado_en = now()
             WHERE producto_id = ? AND local_id = ?
            """;

    /**
     * Reponer lo que ya habia salido. Sin GREATEST ni tope: aca se suma.
     *
     * <p>Sumar no es idempotente por si solo, asi que el que llama tiene que serlo.
     * Lo es: el consumidor de pos.ventas.anuladas pasa por evento_procesado, y Kafka
     * entrega al-menos-una-vez.
     */
    private static final String DEVOLVER = """
            UPDATE stock_local
               SET disponible = disponible + ?, actualizado_en = now()
             WHERE producto_id = ? AND local_id = ?
            """;

    /** Confirmar baja las dos columnas a la vez: lo apartado sale del disponible. */
    private static final String CONFIRMAR = """
            UPDATE stock_local
               SET disponible = disponible - ?,
                   reservado  = GREATEST(reservado - ?, 0),
                   actualizado_en = now()
             WHERE producto_id = ? AND local_id = ?
            """;

    private static final String DISPONIBLE = """
            SELECT COALESCE(disponible - reservado, 0)
              FROM stock_local
             WHERE producto_id = ? AND local_id = ?
            """;

    private final JdbcClient jdbc;

    public StockJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean intentarReservar(UUID productoId, UUID localId, int cantidad) {
        int filas = jdbc.sql(RESERVAR)
                .param(cantidad).param(productoId).param(localId).param(cantidad)
                .update();
        // 0 filas afectadas significa que el WHERE no se cumplio: no hay stock.
        // El cajero se entera en un solo round trip.
        return filas == 1;
    }

    @Override
    public void liberar(UUID productoId, UUID localId, int cantidad) {
        // GREATEST(...,0) hace la operacion idempotente: liberar dos veces la misma
        // reserva no puede dejar el contador en negativo. Con reintentos de Kafka de
        // por medio, "dos veces" no es hipotetico.
        jdbc.sql(LIBERAR).param(cantidad).param(productoId).param(localId).update();
    }

    @Override
    public void devolver(UUID productoId, UUID localId, int cantidad) {
        jdbc.sql(DEVOLVER).param(cantidad).param(productoId).param(localId).update();
    }

    @Override
    public void confirmarSalida(UUID productoId, UUID localId, int cantidad) {
        jdbc.sql(CONFIRMAR)
                .param(cantidad).param(cantidad).param(productoId).param(localId)
                .update();
    }

    @Override
    public int disponibleReal(UUID productoId, UUID localId) {
        return jdbc.sql(DISPONIBLE)
                .param(productoId).param(localId)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }
}

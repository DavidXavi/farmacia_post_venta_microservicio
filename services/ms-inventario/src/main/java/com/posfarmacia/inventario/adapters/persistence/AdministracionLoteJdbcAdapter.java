package com.posfarmacia.inventario.adapters.persistence;

import com.posfarmacia.inventario.usecases.port.out.AdministracionLotePort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Altas y cambios de estado de lotes, en SQL explicito. */
@Repository
public class AdministracionLoteJdbcAdapter implements AdministracionLotePort {

    private static final String INSERTAR = """
            INSERT INTO lotes (id, codigo, producto_id, fecha_vencimiento,
                               cantidad_recibida, cantidad_disponible, costo, local_id, estado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DISPONIBLE')
            """;

    /**
     * El alta del primer lote de un producto crea la fila de stock; las siguientes
     * suman. Un INSERT ... ON CONFLICT lo resuelve en un solo viaje, sin preguntar
     * antes si la fila existe.
     */
    private static final String SUMAR_STOCK = """
            INSERT INTO stock_local (producto_id, local_id, disponible, reservado, actualizado_en)
            VALUES (?, ?, ?, 0, now())
            ON CONFLICT (producto_id, local_id) DO UPDATE
               SET disponible = stock_local.disponible + EXCLUDED.disponible,
                   actualizado_en = now()
            """;

    /**
     * GREATEST evita dejar el contador en negativo si el lote que se retira ya habia
     * sido descontado por otra via. El CHECK de la tabla lo rechazaria, y un lote que
     * no se puede retirar es peor que un contador que se queda en cero.
     */
    private static final String RESTAR_STOCK = """
            UPDATE stock_local
               SET disponible = GREATEST(disponible - ?, reservado), actualizado_en = now()
             WHERE producto_id = ? AND local_id = ?
            """;

    private static final String MOVIMIENTO = """
            INSERT INTO movimientos_inventario
                   (id, lote_id, producto_id, local_id, tipo, cantidad, usuario_id, referencia)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcClient jdbc;

    public AdministracionLoteJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(NuevoLote l) {
        jdbc.sql(INSERTAR)
                .param(l.id()).param(l.codigo()).param(l.productoId())
                .param(l.fechaVencimiento()).param(l.cantidadRecibida())
                .param(l.cantidadRecibida()).param(l.costo()).param(l.localId())
                .update();
    }

    @Override
    public boolean existeCodigoEnLocal(String codigo, UUID localId) {
        return jdbc.sql("SELECT 1 FROM lotes WHERE codigo = ? AND local_id = ?")
                .param(codigo).param(localId)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public Optional<ResumenLote> porId(UUID loteId) {
        return jdbc.sql("""
                        SELECT id, producto_id, local_id, cantidad_disponible, estado
                          FROM lotes WHERE id = ?
                        """)
                .param(loteId)
                .query((rs, fila) -> new ResumenLote(
                        rs.getObject("id", UUID.class),
                        rs.getObject("producto_id", UUID.class),
                        rs.getObject("local_id", UUID.class),
                        rs.getInt("cantidad_disponible"),
                        rs.getString("estado")))
                .optional();
    }

    @Override
    public void cambiarEstado(UUID loteId, String estado) {
        jdbc.sql("UPDATE lotes SET estado = ? WHERE id = ?")
                .param(estado).param(loteId).update();
    }

    @Override
    public void sumarStock(UUID productoId, UUID localId, int cantidad) {
        jdbc.sql(SUMAR_STOCK).param(productoId).param(localId).param(cantidad).update();
    }

    @Override
    public void restarStock(UUID productoId, UUID localId, int cantidad) {
        jdbc.sql(RESTAR_STOCK).param(cantidad).param(productoId).param(localId).update();
    }

    @Override
    public void registrarMovimiento(UUID loteId, UUID productoId, UUID localId, String tipo,
            int cantidad, UUID usuarioId, String referencia) {
        jdbc.sql(MOVIMIENTO)
                .param(UUID.randomUUID()).param(loteId).param(productoId).param(localId)
                .param(tipo).param(cantidad).param(usuarioId).param(referencia)
                .update();
    }
}

package com.posfarmacia.inventario.adapters.persistence;

import com.posfarmacia.inventario.domain.EstadoReserva;
import com.posfarmacia.inventario.usecases.port.out.ReservaPort;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReservaJdbcAdapter implements ReservaPort {

    /**
     * ON CONFLICT DO NOTHING mas el SELECT posterior: si dos peticiones concurrentes
     * de la misma venta y producto llegan a la vez (POS que reintenta antes de que
     * responda la primera), solo una inserta y las dos ven la misma reserva.
     * Resolverlo con un "SELECT y si no existe INSERT" dejaria la carrera abierta
     * justo en el punto que se quiere cerrar.
     */
    private static final String INSERTAR = """
            INSERT INTO reservas (id, venta_id, producto_id, local_id, cantidad, estado, expira_en)
            VALUES (?, ?, ?, ?, ?, 'ACTIVA', ?)
            ON CONFLICT (venta_id, producto_id) DO NOTHING
            """;

    private static final String POR_VENTA_PRODUCTO = """
            SELECT id, venta_id, producto_id, local_id, cantidad, estado, expira_en
              FROM reservas WHERE venta_id = ? AND producto_id = ?
            """;

    private static final String POR_ID = """
            SELECT id, venta_id, producto_id, local_id, cantidad, estado, expira_en
              FROM reservas WHERE id = ?
            """;

    private static final String ACTIVAS_DE_VENTA = """
            SELECT id, venta_id, producto_id, local_id, cantidad, estado, expira_en
              FROM reservas WHERE venta_id = ? AND estado = 'ACTIVA'
            """;

    private static final String CAMBIAR_ESTADO = """
            UPDATE reservas SET estado = ?, cerrada_en = now()
             WHERE id = ? AND estado = 'ACTIVA'
            """;

    /**
     * SKIP LOCKED para que varias replicas del job de limpieza se repartan el trabajo
     * sin esperarse. Sin el, las seis replicas pelearian por las mismas filas y cinco
     * quedarian bloqueadas haciendo nada.
     */
    private static final String VENCIDAS = """
            SELECT id, venta_id, producto_id, local_id, cantidad, estado, expira_en
              FROM reservas
             WHERE estado = 'ACTIVA' AND expira_en < now()
             ORDER BY expira_en
             LIMIT ?
             FOR UPDATE SKIP LOCKED
            """;

    private static final RowMapper<ReservaGuardada> MAPPER = (ResultSet rs, int fila) ->
            new ReservaGuardada(
                    rs.getObject("id", UUID.class),
                    rs.getObject("venta_id", UUID.class),
                    rs.getObject("producto_id", UUID.class),
                    rs.getObject("local_id", UUID.class),
                    rs.getInt("cantidad"),
                    EstadoReserva.valueOf(rs.getString("estado")),
                    rs.getTimestamp("expira_en").toInstant());

    private final JdbcClient jdbc;

    public ReservaJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ReservaGuardada guardarSiNoExiste(UUID ventaId, UUID productoId, UUID localId,
            int cantidad, Instant expiraEn) {
        jdbc.sql(INSERTAR)
                .param(UUID.randomUUID())
                .param(ventaId).param(productoId).param(localId).param(cantidad)
                .param(java.sql.Timestamp.from(expiraEn))
                .update();

        return jdbc.sql(POR_VENTA_PRODUCTO)
                .param(ventaId).param(productoId)
                .query(MAPPER)
                .single();
    }

    @Override
    public Optional<ReservaGuardada> porId(UUID reservaId) {
        return jdbc.sql(POR_ID).param(reservaId).query(MAPPER).optional();
    }

    @Override
    public List<ReservaGuardada> activasDeVenta(UUID ventaId) {
        return jdbc.sql(ACTIVAS_DE_VENTA).param(ventaId).query(MAPPER).list();
    }

    @Override
    public void cambiarEstado(UUID reservaId, EstadoReserva nuevo) {
        jdbc.sql(CAMBIAR_ESTADO).param(nuevo.name()).param(reservaId).update();
    }

    @Override
    public List<ReservaGuardada> vencidas(int limite) {
        return jdbc.sql(VENCIDAS).param(limite).query(MAPPER).list();
    }
}

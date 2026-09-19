package com.posfarmacia.credito.adapters.persistence;

import com.posfarmacia.credito.usecases.port.out.CreditoPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CreditoJdbcAdapter implements CreditoPort {

    private static final String LINEA = """
            SELECT id, cliente_id, monto_autorizado, saldo_disponible, reservado, estado
              FROM lineas_credito WHERE cliente_id = ?
            """;

    /** El mismo UPDATE condicional del stock, aplicado a dinero. */
    private static final String RESERVAR = """
            UPDATE lineas_credito
               SET reservado = reservado + ?
             WHERE id = ? AND saldo_disponible - reservado >= ?
            """;

    private static final String LIBERAR = """
            UPDATE lineas_credito SET reservado = GREATEST(reservado - ?, 0) WHERE id = ?
            """;

    private static final String CONFIRMAR = """
            UPDATE lineas_credito
               SET saldo_disponible = saldo_disponible - ?,
                   reservado = GREATEST(reservado - ?, 0),
                   version = version + 1
             WHERE id = ?
            """;

    private static final String LEDGER = """
            INSERT INTO movimientos_credito
                (id, linea_credito_id, venta_id, tipo, monto, saldo_resultante)
            SELECT ?, ?, ?, 'CARGO', ?, saldo_disponible FROM lineas_credito WHERE id = ?
            """;

    private static final String GUARDAR_RESERVA = """
            INSERT INTO reservas_credito (id, linea_credito_id, venta_id, monto, estado, expira_en)
            VALUES (?, ?, ?, ?, 'ACTIVA', ?)
            ON CONFLICT (venta_id) DO NOTHING
            """;

    private static final String RESERVA_DE_VENTA = """
            SELECT id FROM reservas_credito WHERE venta_id = ? AND estado = 'ACTIVA'
            """;

    private static final String CERRAR_RESERVA = """
            UPDATE reservas_credito SET estado = ?, cerrada_en = now()
             WHERE id = ? AND estado = 'ACTIVA'
            """;

    private final JdbcClient jdbc;

    public CreditoJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Linea> lineaDe(UUID clienteId) {
        return jdbc.sql(LINEA).param(clienteId)
                .query((rs, i) -> new Linea(
                        rs.getObject("id", UUID.class),
                        rs.getObject("cliente_id", UUID.class),
                        rs.getBigDecimal("monto_autorizado"),
                        rs.getBigDecimal("saldo_disponible"),
                        rs.getBigDecimal("reservado"),
                        rs.getString("estado")))
                .optional();
    }

    @Override
    public boolean intentarReservar(UUID lineaId, BigDecimal monto) {
        return jdbc.sql(RESERVAR).param(monto).param(lineaId).param(monto).update() == 1;
    }

    @Override
    public void liberarReserva(UUID lineaId, BigDecimal monto) {
        jdbc.sql(LIBERAR).param(monto).param(lineaId).update();
    }

    @Override
    public void confirmarCargo(UUID lineaId, UUID ventaId, BigDecimal monto) {
        jdbc.sql(CONFIRMAR).param(monto).param(monto).param(lineaId).update();
        // El ledger se escribe DESPUES del UPDATE para que saldo_resultante refleje el
        // saldo ya descontado: una fila de ledger que no cuadra con el saldo es lo
        // primero que observa una auditoria.
        jdbc.sql(LEDGER)
                .param(UUID.randomUUID()).param(lineaId).param(ventaId).param(monto).param(lineaId)
                .update();
    }

    @Override
    public Optional<UUID> reservaDeVenta(UUID ventaId) {
        return jdbc.sql(RESERVA_DE_VENTA).param(ventaId).query(UUID.class).optional();
    }

    @Override
    public UUID guardarReserva(UUID lineaId, UUID ventaId, BigDecimal monto, Instant expira) {
        UUID id = UUID.randomUUID();
        jdbc.sql(GUARDAR_RESERVA)
                .param(id).param(lineaId).param(ventaId).param(monto)
                .param(java.sql.Timestamp.from(expira))
                .update();
        return reservaDeVenta(ventaId).orElse(id);
    }

    @Override
    public void cerrarReserva(UUID reservaId, String estado) {
        jdbc.sql(CERRAR_RESERVA).param(estado).param(reservaId).update();
    }
}

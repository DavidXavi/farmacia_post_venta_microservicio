package com.posfarmacia.ventas.adapters.persistence;

import com.posfarmacia.ventas.domain.EstadoSaga;
import com.posfarmacia.ventas.usecases.port.out.SagaPort;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SagaJdbcAdapter implements SagaPort {

    private static final String ABRIR = """
            INSERT INTO saga_venta (venta_id, estado) VALUES (?, 'EN_CURSO')
            ON CONFLICT (venta_id) DO NOTHING
            """;

    private static final String COMPLETA = """
            SELECT stock_confirmado AND comprobante_emitido FROM saga_venta WHERE venta_id = ?
            """;

    private static final String CERRAR = """
            UPDATE saga_venta SET estado = ?, cerrada_en = now() WHERE venta_id = ?
            """;

    private static final String COMPENSAR = """
            UPDATE saga_venta SET estado = 'COMPENSANDO', motivo_compensacion = ?
             WHERE venta_id = ?
            """;

    private final JdbcClient jdbc;

    public SagaJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void abrir(UUID ventaId) {
        jdbc.sql(ABRIR).param(ventaId).update();
    }

    @Override
    public void marcar(UUID ventaId, Paso paso) {
        // El nombre de columna no viene del exterior, sale del enum: no hay forma de
        // inyectar SQL aqui aunque la sentencia se arme por concatenacion.
        String columna = switch (paso) {
            case STOCK -> "stock_confirmado";
            case CREDITO -> "credito_confirmado";
            case COMPROBANTE -> "comprobante_emitido";
        };
        jdbc.sql("UPDATE saga_venta SET " + columna + " = true WHERE venta_id = ?")
                .param(ventaId)
                .update();
    }

    @Override
    public boolean completa(UUID ventaId) {
        return Boolean.TRUE.equals(
                jdbc.sql(COMPLETA).param(ventaId).query(Boolean.class).optional().orElse(false));
    }

    @Override
    public void cerrar(UUID ventaId, EstadoSaga estado) {
        jdbc.sql(CERRAR).param(estado.name()).param(ventaId).update();
    }

    @Override
    public void compensar(UUID ventaId, String motivo) {
        jdbc.sql(COMPENSAR).param(motivo).param(ventaId).update();
    }
}

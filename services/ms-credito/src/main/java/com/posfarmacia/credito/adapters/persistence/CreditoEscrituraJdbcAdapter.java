package com.posfarmacia.credito.adapters.persistence;

import com.posfarmacia.credito.usecases.port.out.CreditoEscrituraPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Alta y ajuste de lineas de credito. */
@Repository
public class CreditoEscrituraJdbcAdapter implements CreditoEscrituraPort {

    /**
     * Upsert sobre el indice unico por cliente.
     *
     * <p>Al ajustar el monto, el saldo disponible se mueve en la misma cantidad que el
     * autorizado: subir el limite de 1000 a 1500 con 300 ya gastados tiene que dejar
     * 1200 disponibles, no 1500. Escribir el saldo igual al autorizado le perdonaria al
     * cliente lo que ya debe.
     */
    private static final String GUARDAR = """
            INSERT INTO lineas_credito (id, cliente_id, monto_autorizado, saldo_disponible,
                                        reservado, vigencia_inicio, vigencia_fin, estado)
            VALUES (?, ?, ?, ?, 0, ?, ?, 'ACTIVA')
            ON CONFLICT (cliente_id) DO UPDATE
               SET saldo_disponible = GREATEST(
                       lineas_credito.saldo_disponible
                           + (EXCLUDED.monto_autorizado - lineas_credito.monto_autorizado), 0),
                   monto_autorizado = EXCLUDED.monto_autorizado,
                   vigencia_inicio  = EXCLUDED.vigencia_inicio,
                   vigencia_fin     = EXCLUDED.vigencia_fin,
                   estado           = 'ACTIVA',
                   version          = lineas_credito.version + 1
            RETURNING id, saldo_disponible
            """;

    private static final String MOVIMIENTO = """
            INSERT INTO movimientos_credito (id, linea_credito_id, venta_id, tipo, monto,
                                             saldo_resultante)
            VALUES (?, ?, NULL, ?, ?, ?)
            """;

    private final JdbcClient jdbc;

    public CreditoEscrituraJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID guardarLinea(UUID clienteId, BigDecimal montoAutorizado,
            LocalDate vigenciaInicio, LocalDate vigenciaFin) {
        return jdbc.sql(GUARDAR)
                .param(UUID.randomUUID()).param(clienteId).param(montoAutorizado)
                .param(montoAutorizado).param(vigenciaInicio).param(vigenciaFin)
                .query((rs, fila) -> rs.getObject("id", UUID.class))
                .single();
    }

    @Override
    public void registrarMovimiento(UUID lineaId, String tipo, BigDecimal monto,
            BigDecimal saldoResultante) {
        jdbc.sql(MOVIMIENTO)
                .param(UUID.randomUUID()).param(lineaId).param(tipo).param(monto)
                .param(saldoResultante)
                .update();
    }
}

package com.posfarmacia.credito.usecases.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Alta y ajuste de la linea de credito.
 *
 * <p>Aparte de {@link CreditoPort}, que es lo que ms-ventas usa en cada cobro a
 * credito. Otorgar credito lo hace un administrador un punado de veces; reservarlo
 * pasa en cada venta fiada.
 */
public interface CreditoEscrituraPort {

    /**
     * Alta o ajuste de la linea del cliente.
     *
     * <p>Un cliente tiene una sola linea, lo impone un indice unico. Volver a otorgar
     * es subir o bajar el monto autorizado, no crear una segunda: dos lineas para el
     * mismo cliente serian dos limites simultaneos y ninguna forma de saber cual manda.
     *
     * @return el id de la linea, la nueva o la que ya existia
     */
    UUID guardarLinea(UUID clienteId, BigDecimal montoAutorizado,
                      LocalDate vigenciaInicio, LocalDate vigenciaFin);

    /** Fila del ledger. Append-only: lo impone un trigger, no la disciplina. */
    void registrarMovimiento(UUID lineaId, String tipo, BigDecimal monto,
                             BigDecimal saldoResultante);
}

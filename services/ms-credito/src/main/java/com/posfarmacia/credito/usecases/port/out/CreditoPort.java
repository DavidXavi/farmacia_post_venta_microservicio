package com.posfarmacia.credito.usecases.port.out;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * La linea de credito y su ledger.
 *
 * <p>Este servicio existe aparte de clientes porque aqui hay dinero. Un ledger
 * append-only, conciliable y auditable no es lo mismo que un CRUD de datos de contacto,
 * y mezclarlos hace que el segundo arrastre al primero cada vez que cambia.
 */
public interface CreditoPort {

    record Linea(UUID id, UUID clienteId, BigDecimal montoAutorizado,
                 BigDecimal saldoDisponible, BigDecimal reservado, String estado) {

        /** Lo que realmente se puede usar ahora mismo. */
        public BigDecimal disponibleEfectivo() {
            return saldoDisponible.subtract(reservado).max(BigDecimal.ZERO);
        }
    }

    Optional<Linea> lineaDe(UUID clienteId);

    /**
     * Aparta credito de forma atomica, igual que el stock: un UPDATE condicional.
     *
     * @return true si alcanzaba
     */
    boolean intentarReservar(UUID lineaId, BigDecimal monto);

    void liberarReserva(UUID lineaId, BigDecimal monto);

    /** Convierte la reserva en cargo firme y escribe la fila del ledger. */
    void confirmarCargo(UUID lineaId, UUID ventaId, BigDecimal monto);

    Optional<UUID> reservaDeVenta(UUID ventaId);

    UUID guardarReserva(UUID lineaId, UUID ventaId, BigDecimal monto, java.time.Instant expira);

    void cerrarReserva(UUID reservaId, String estado);
}

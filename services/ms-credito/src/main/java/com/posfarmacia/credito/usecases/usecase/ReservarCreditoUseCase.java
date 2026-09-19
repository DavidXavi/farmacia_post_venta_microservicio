package com.posfarmacia.credito.usecases.usecase;

import com.posfarmacia.contracts.api.CargoCreditoRespuesta;
import com.posfarmacia.contracts.api.CargoCreditoSolicitud;
import com.posfarmacia.credito.usecases.port.out.CreditoPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reservar credito para una venta. Junto con la reserva de stock, la unica otra llamada
 * sincrona que puede impedir una venta.
 *
 * <p>Mismo patron que el stock y por la misma razon: reserva con TTL, UPDATE condicional
 * atomico, idempotencia por ventaId. Si la caja se cuelga, el credito del cliente no
 * queda congelado para siempre.
 */
@Service
public class ReservarCreditoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReservarCreditoUseCase.class);
    private static final Duration TTL = Duration.ofMinutes(15);

    private final CreditoPort credito;

    public ReservarCreditoUseCase(CreditoPort credito) {
        this.credito = credito;
    }

    @Transactional
    public CargoCreditoRespuesta reservar(CargoCreditoSolicitud solicitud) {
        // Idempotencia: el reintento del POS no aparta el doble.
        var yaReservada = credito.reservaDeVenta(solicitud.ventaId());
        if (yaReservada.isPresent()) {
            var linea = credito.lineaDe(solicitud.clienteId()).orElseThrow();
            return new CargoCreditoRespuesta(yaReservada.get(), linea.id(), true,
                    linea.disponibleEfectivo(), null);
        }

        var linea = credito.lineaDe(solicitud.clienteId()).orElse(null);
        if (linea == null) {
            return new CargoCreditoRespuesta(null, null, false, BigDecimal.ZERO,
                    "El cliente no tiene linea de credito");
        }
        if (!"ACTIVA".equals(linea.estado())) {
            return new CargoCreditoRespuesta(null, linea.id(), false, BigDecimal.ZERO,
                    "La linea de credito esta " + linea.estado());
        }

        if (!credito.intentarReservar(linea.id(), solicitud.monto())) {
            // Igual que con el stock: no es excepcion. El cajero necesita el saldo real
            // para proponerle al cliente pagar la diferencia en efectivo.
            return new CargoCreditoRespuesta(null, linea.id(), false,
                    linea.disponibleEfectivo(),
                    "Saldo insuficiente: disponible " + linea.disponibleEfectivo());
        }

        UUID reservaId = credito.guardarReserva(linea.id(), solicitud.ventaId(),
                solicitud.monto(), Instant.now().plus(TTL));

        log.info("Credito reservado para venta {}: {} sobre la linea {}",
                solicitud.ventaId(), solicitud.monto(), linea.id());

        return new CargoCreditoRespuesta(reservaId, linea.id(), true,
                linea.disponibleEfectivo().subtract(solicitud.monto()), null);
    }
}

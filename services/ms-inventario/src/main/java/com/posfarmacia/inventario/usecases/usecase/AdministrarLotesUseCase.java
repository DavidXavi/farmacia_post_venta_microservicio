package com.posfarmacia.inventario.usecases.usecase;

import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.OperacionAuditada;
import com.posfarmacia.contracts.eventos.StockMovimiento;
import com.posfarmacia.inventario.usecases.port.out.AdministracionLotePort;
import com.posfarmacia.plataforma.outbox.OutboxRegistrador;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que hace el encargado de inventario: recibir mercaderia y sacar lotes de
 * circulacion.
 *
 * <p>Las tres operaciones mueven el contador de stock ademas del lote, y eso no es
 * opcional: un lote bloqueado cuyo stock sigue contando es un producto que el sistema
 * ofrece y el anaquel no tiene. El lote y el contador cambian en la misma transaccion.
 *
 * <p>Bloquear y retirar publican en {@code pos.auditoria}. Sacar mercaderia de
 * circulacion es de las pocas operaciones donde alguien va a preguntar meses despues
 * quien lo hizo y por que.
 */
@Service
public class AdministrarLotesUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdministrarLotesUseCase.class);

    private static final String DISPONIBLE = "DISPONIBLE";
    private static final String BLOQUEADO = "BLOQUEADO";
    private static final String RETIRADO = "RETIRADO";

    private final AdministracionLotePort lotes;
    private final OutboxRegistrador outbox;
    private final Clock reloj;

    public AdministrarLotesUseCase(AdministracionLotePort lotes, OutboxRegistrador outbox,
            Clock reloj) {
        this.lotes = lotes;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    public record AltaLote(String codigo, UUID productoId, UUID localId,
                           LocalDate fechaVencimiento, int cantidadRecibida, BigDecimal costo) {
    }

    @Transactional
    public UUID registrar(AltaLote alta, UUID usuarioId) {
        if (alta.cantidadRecibida() <= 0) {
            throw new IllegalArgumentException("La cantidad recibida tiene que ser mayor que cero");
        }
        if (alta.fechaVencimiento() == null) {
            throw new IllegalArgumentException("El lote necesita fecha de vencimiento");
        }
        // Un lote ya vencido no puede entrar: si entra, FEFO lo elegiria primero
        // justamente por vencer antes, y saldria a la calle.
        if (alta.fechaVencimiento().isBefore(LocalDate.now(reloj))) {
            throw new IllegalArgumentException(
                    "El lote ya vencio el " + alta.fechaVencimiento() + ": no puede recibirse");
        }
        if (lotes.existeCodigoEnLocal(alta.codigo(), alta.localId())) {
            throw new IllegalStateException(
                    "Ya existe el lote " + alta.codigo() + " en este local");
        }

        UUID id = UUID.randomUUID();
        lotes.insertar(new AdministracionLotePort.NuevoLote(id, alta.codigo().trim(),
                alta.productoId(), alta.localId(), alta.fechaVencimiento(),
                alta.cantidadRecibida(), alta.costo()));
        lotes.sumarStock(alta.productoId(), alta.localId(), alta.cantidadRecibida());
        lotes.registrarMovimiento(id, alta.productoId(), alta.localId(), "INGRESO_COMPRA",
                alta.cantidadRecibida(), usuarioId, "Alta de lote " + alta.codigo());

        publicarMovimiento(id, alta.productoId(), alta.localId(), "INGRESO_COMPRA",
                alta.cantidadRecibida(), usuarioId, "Alta de lote " + alta.codigo());

        log.info("Lote {} recibido en el local {}: {} unidades del producto {}",
                alta.codigo(), alta.localId(), alta.cantidadRecibida(), alta.productoId());
        return id;
    }

    /** Bloquear: el lote sigue en el anaquel pero deja de poder venderse. */
    @Transactional
    public void bloquear(UUID loteId, UUID usuarioId, String motivo) {
        cambiar(loteId, BLOQUEADO, "BLOQUEO_LOTE", usuarioId, motivo);
    }

    /** Retirar: el lote sale fisicamente. Es definitivo. */
    @Transactional
    public void retirar(UUID loteId, UUID usuarioId, String motivo) {
        cambiar(loteId, RETIRADO, "RETIRO_LOTE", usuarioId, motivo);
    }

    private void cambiar(UUID loteId, String nuevoEstado, String tipoMovimiento,
            UUID usuarioId, String motivo) {
        var lote = lotes.porId(loteId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el lote " + loteId));

        if (nuevoEstado.equals(lote.estado())) {
            return;   // idempotente: repetir la operacion no descuenta el stock dos veces
        }
        if (RETIRADO.equals(lote.estado())) {
            throw new IllegalStateException("El lote ya fue retirado: no admite cambios");
        }

        lotes.cambiarEstado(loteId, nuevoEstado);

        // Solo se descuenta si el lote estaba contando. Un lote que ya estaba bloqueado
        // no suma al disponible, asi que retirarlo no tiene que restar de nuevo.
        int enCirculacion = DISPONIBLE.equals(lote.estado()) ? lote.cantidadDisponible() : 0;
        if (enCirculacion > 0) {
            lotes.restarStock(lote.productoId(), lote.localId(), enCirculacion);
        }
        lotes.registrarMovimiento(loteId, lote.productoId(), lote.localId(), tipoMovimiento,
                enCirculacion, usuarioId, motivo);

        publicarMovimiento(loteId, lote.productoId(), lote.localId(), tipoMovimiento,
                enCirculacion, usuarioId, motivo);
        auditar(loteId, usuarioId, tipoMovimiento, lote.estado(), nuevoEstado, motivo);

        log.warn("Lote {} paso de {} a {} por {}: {}", loteId, lote.estado(), nuevoEstado,
                usuarioId, motivo);
    }

    private void publicarMovimiento(UUID loteId, UUID productoId, UUID localId, String tipo,
            int cantidad, UUID usuarioId, String referencia) {
        UUID movimientoId = UUID.randomUUID();
        outbox.registrar("lote", loteId, Topicos.STOCK_MOVIMIENTOS, productoId,
                new StockMovimiento(movimientoId, loteId, productoId, localId, tipo,
                        cantidad, usuarioId, referencia, Instant.now(reloj)));
    }

    private void auditar(UUID loteId, UUID usuarioId, String accion, String antes,
            String despues, String motivo) {
        outbox.registrar("lote", loteId, Topicos.AUDITORIA, loteId,
                new OperacionAuditada(usuarioId, "ms-inventario", accion, "lote",
                        loteId.toString(), motivo, antes, despues, null, Instant.now(reloj)));
    }
}

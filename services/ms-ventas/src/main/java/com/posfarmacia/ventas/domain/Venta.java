package com.posfarmacia.ventas.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * La venta. Es lo unico que ms-ventas es dueno de verdad.
 *
 * <p>Todo lo demas que aparece aca (producto, cliente, convenio, linea de credito) son
 * ids de otros servicios, sin integridad referencial que los respalde. Eso no es un
 * descuido: la consistencia entre servicios la garantiza la saga, no el motor. Si
 * manana se borra un producto del catalogo, esta venta debe seguir mostrando lo que
 * se vendio, y por eso la linea guarda el nombre y el precio como copia.
 *
 * <p>Dominio puro: sin Spring, sin JPA, sin anotaciones. Se prueba sin levantar nada.
 */
public final class Venta {

    private final UUID id;
    private final Instant fecha;
    private final UUID localId;
    private final UUID cajaId;
    private final UUID sesionCajaId;
    private final UUID usuarioId;
    private final List<LineaVenta> lineas = new ArrayList<>();

    private UUID clienteId;
    private UUID convenioSeguroId;
    private UUID lineaCreditoId;
    private EstadoVenta estado;

    public Venta(UUID id, Instant fecha, UUID localId, UUID cajaId, UUID sesionCajaId, UUID usuarioId) {
        this.id = id;
        this.fecha = fecha;
        this.localId = localId;
        this.cajaId = cajaId;
        this.sesionCajaId = sesionCajaId;
        this.usuarioId = usuarioId;
        this.estado = EstadoVenta.BORRADOR;
    }

    /** Reconstruccion desde la base. */
    public Venta(UUID id, Instant fecha, UUID localId, UUID cajaId, UUID sesionCajaId,
            UUID usuarioId, UUID clienteId, UUID convenioSeguroId, UUID lineaCreditoId,
            EstadoVenta estado, List<LineaVenta> lineasExistentes) {
        this(id, fecha, localId, cajaId, sesionCajaId, usuarioId);
        this.clienteId = clienteId;
        this.convenioSeguroId = convenioSeguroId;
        this.lineaCreditoId = lineaCreditoId;
        this.estado = estado;
        this.lineas.addAll(lineasExistentes);
    }

    public void agregarLinea(LineaVenta linea) {
        exigirBorrador("agregar una linea");
        lineas.add(linea);
    }

    public void quitarLinea(UUID detalleId) {
        exigirBorrador("quitar una linea");
        lineas.removeIf(l -> l.id().equals(detalleId));
    }

    /**
     * Aplica una promocion a una linea: le pone el descuento y deja anotado cual fue.
     *
     * <p>Una linea admite una promocion, no varias. Acumular dos descuentos sobre el
     * mismo producto es la forma mas rapida de vender por debajo del costo sin que
     * nadie lo note hasta el cierre de mes, asi que aplicar una segunda reemplaza a la
     * primera en vez de sumarse.
     */
    public void aplicarPromocion(UUID detalleId, UUID promocionId, BigDecimal descuento) {
        exigirBorrador("aplicar una promocion");
        int posicion = indiceDe(detalleId);
        LineaVenta linea = lineas.get(posicion);
        if (descuento == null || descuento.signum() < 0) {
            throw new VentaInvalidaException("El descuento no puede ser negativo");
        }
        BigDecimal tope = linea.precioUnitario().multiply(BigDecimal.valueOf(linea.cantidad()));
        if (descuento.compareTo(tope) > 0) {
            throw new VentaInvalidaException(
                    "El descuento (" + descuento + ") supera el precio de la linea (" + tope + ")");
        }
        lineas.set(posicion, new LineaVenta(linea.id(), linea.ventaId(), linea.productoId(),
                linea.nombreProducto(), linea.cantidad(), linea.precioUnitario(),
                linea.tasaImpuesto(), descuento, promocionId, linea.recetaId()));
    }

    private int indiceDe(UUID detalleId) {
        for (int i = 0; i < lineas.size(); i++) {
            if (lineas.get(i).id().equals(detalleId)) {
                return i;
            }
        }
        throw new VentaInvalidaException("La venta no tiene la linea " + detalleId);
    }

    public void identificarCliente(UUID clienteId, UUID convenioSeguroId) {
        exigirBorrador("identificar al cliente");
        this.clienteId = clienteId;
        this.convenioSeguroId = convenioSeguroId;
    }

    public void asociarCredito(UUID lineaCreditoId) {
        exigirBorrador("asociar credito");
        this.lineaCreditoId = lineaCreditoId;
    }

    /**
     * Confirmar es un cambio de estado y nada mas.
     *
     * <p>Toda la coordinacion con inventario, credito y facturacion pasa DESPUES, por
     * eventos. Si esta operacion intentara hacer las cuatro cosas de golpe, seria una
     * transaccion distribuida: el patron que esta arquitectura existe para evitar.
     */
    public void confirmar() {
        exigirBorrador("confirmar");
        if (lineas.isEmpty()) {
            throw new VentaInvalidaException("No se puede confirmar una venta sin lineas");
        }
        this.estado = EstadoVenta.CONFIRMADA;
    }

    public void anular() {
        if (estado == EstadoVenta.ANULADA) {
            return;   // idempotente: anular dos veces no dispara dos compensaciones
        }
        this.estado = EstadoVenta.ANULADA;
    }

    private void exigirBorrador(String accion) {
        if (estado != EstadoVenta.BORRADOR) {
            throw new VentaInvalidaException(
                    "No se puede " + accion + ": la venta esta " + estado);
        }
    }

    public BigDecimal subtotal() {
        return lineas.stream()
                .map(LineaVenta::baseImponible)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal descuento() {
        return lineas.stream()
                .map(LineaVenta::descuento)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal impuesto() {
        return lineas.stream()
                .map(LineaVenta::impuesto)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal total() {
        return subtotal().add(impuesto()).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID id() {
        return id;
    }

    public Instant fecha() {
        return fecha;
    }

    public UUID localId() {
        return localId;
    }

    public UUID cajaId() {
        return cajaId;
    }

    public UUID sesionCajaId() {
        return sesionCajaId;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public UUID clienteId() {
        return clienteId;
    }

    public UUID convenioSeguroId() {
        return convenioSeguroId;
    }

    public UUID lineaCreditoId() {
        return lineaCreditoId;
    }

    public EstadoVenta estado() {
        return estado;
    }

    public List<LineaVenta> lineas() {
        return Collections.unmodifiableList(lineas);
    }
}

package com.posfarmacia.ventas.adapters.web;

import com.posfarmacia.ventas.domain.Venta;
import com.posfarmacia.ventas.usecases.usecase.AnularVentaUseCase;
import com.posfarmacia.ventas.usecases.usecase.AplicarPromocionesUseCase;
import com.posfarmacia.ventas.usecases.usecase.ArmarVentaUseCase;
import com.posfarmacia.ventas.usecases.usecase.ConfirmarVentaUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ventas")
public class VentasController {

    public record IniciarPeticion(UUID localId, @NotNull UUID cajaId,
                                  @NotNull UUID sesionCajaId, @NotNull UUID usuarioId,
                                  String clienteDni) {
    }

    public record LineaPeticion(@NotNull UUID productoId, @Positive int cantidad, UUID recetaId) {
    }

    public record IdentificarPeticion(@NotBlank String dni) {
    }

    public record ConfirmarPeticion(String tipoComprobante, String serieComprobante) {
    }

    public record PagoPeticion(@NotNull UUID formaPagoId, java.math.BigDecimal monto,
                               String codigoAutorizacion) {
    }

    public record ConvenioPeticion(@NotNull UUID convenioId) {
    }

    public record PromocionPeticion(@NotNull UUID promocionId) {
    }

    public record CopagoVista(java.math.BigDecimal montoCubierto, java.math.BigDecimal copago) {
    }

    public record AnularPeticion(@NotNull UUID usuarioId, @NotNull String motivo,
                                 boolean comprobanteEmitido) {
    }

    /**
     * Vista de la venta con los nombres que la caja ya usaba en el monolito.
     *
     * <p>Partir el backend no puede obligar a reescribir el frontend: el contrato con
     * la pantalla es el mismo, cambia quien lo sirve.
     */
    public record VentaVista(UUID id, String estado, UUID clienteId,
                             BigDecimal subtotal, BigDecimal descuento, BigDecimal impuesto,
                             BigDecimal total, BigDecimal totalPagado,
                             String numeroComprobante, List<DetalleVista> detalles) {
    }

    public record DetalleVista(UUID id, UUID productoId, String nombreProducto, int cantidad,
                               BigDecimal precioUnitario, BigDecimal descuentoMonto,
                               BigDecimal impuestoMonto, BigDecimal subtotal) {
    }

    private final ArmarVentaUseCase armar;
    private final ConfirmarVentaUseCase confirmar;
    private final AnularVentaUseCase anular;
    private final AplicarPromocionesUseCase promociones;

    public VentasController(ArmarVentaUseCase armar, ConfirmarVentaUseCase confirmar,
            AnularVentaUseCase anular, AplicarPromocionesUseCase promociones) {
        this.armar = armar;
        this.confirmar = confirmar;
        this.anular = anular;
        this.promociones = promociones;
    }

    @PostMapping
    public ResponseEntity<VentaVista> iniciar(@Valid @RequestBody IniciarPeticion p,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.oauth2.jwt.Jwt jwt) {
        // El local sale del token si la caja no lo manda: ya viaja firmado en el JWT y
        // pedirselo al cliente seria confiar en el cliente para algo que ya esta probado.
        UUID local = p.localId() != null
                ? p.localId()
                : UUID.fromString(jwt.getClaimAsString("local_id"));

        var venta = armar.iniciar(local, p.cajaId(), p.sesionCajaId(), p.usuarioId());
        if (p.clienteDni() != null && !p.clienteDni().isBlank()) {
            venta = armar.identificarCliente(venta.id(), p.clienteDni());
        }
        return ResponseEntity.ok(vista(venta));
    }

    @PostMapping({"/{ventaId}/detalles", "/{ventaId}/lineas"})
    public ResponseEntity<VentaVista> agregarLinea(@PathVariable UUID ventaId,
            @Valid @RequestBody LineaPeticion p) {
        return ResponseEntity.ok(vista(
                armar.agregarLinea(ventaId, p.productoId(), p.cantidad(), p.recetaId())));
    }

    @DeleteMapping({"/{ventaId}/detalles/{detalleId}", "/{ventaId}/lineas/{detalleId}"})
    public ResponseEntity<VentaVista> quitarLinea(@PathVariable UUID ventaId,
            @PathVariable UUID detalleId) {
        return ResponseEntity.ok(vista(armar.quitarLinea(ventaId, detalleId)));
    }

    @PostMapping("/{ventaId}/cliente")
    public ResponseEntity<VentaVista> identificar(@PathVariable UUID ventaId,
            @Valid @RequestBody IdentificarPeticion p) {
        return ResponseEntity.ok(vista(armar.identificarCliente(ventaId, p.dni())));
    }

    /**
     * Confirmar la venta.
     *
     * <p>Exige Idempotency-Key. No es opcional: un POS reintenta, y un reintento que
     * confirma dos veces la misma venta no es un problema tecnico, es un problema legal
     * con el cliente parado en el mostrador. El filtro de plataforma devuelve la
     * respuesta original si la clave ya se vio, sin volver a ejecutar nada.
     */
    @PostMapping("/{ventaId}/confirmar")
    public ResponseEntity<VentaVista> confirmar(@PathVariable UUID ventaId,
            @RequestHeader("Idempotency-Key") String claveIdempotencia,
            @Valid @RequestBody(required = false) ConfirmarPeticion p) {
        return ResponseEntity.ok(vista(confirmar.confirmar(ventaId, p == null ? null : p.tipoComprobante())));
    }

    @PostMapping("/{ventaId}/anular")
    public ResponseEntity<Void> anular(@PathVariable UUID ventaId,
            @RequestHeader("Idempotency-Key") String claveIdempotencia,
            @Valid @RequestBody AnularPeticion p) {
        anular.anular(ventaId, p.usuarioId(), p.motivo(), p.comprobanteEmitido());
        return ResponseEntity.accepted().build();
    }

    /**
     * Registrar un pago. Exige Idempotency-Key por la misma razon que confirmar: un
     * doble clic del cajero no puede cobrar dos veces.
     */
    @PostMapping("/{ventaId}/pagos")
    public ResponseEntity<VentaVista> pagar(@PathVariable UUID ventaId,
            @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia,
            @Valid @RequestBody PagoPeticion p) {
        return ResponseEntity.ok(vista(armar.registrarPago(ventaId, p.formaPagoId(),
                p.monto(), p.codigoAutorizacion())));
    }

    /** Aplica el convenio de seguro del cliente y devuelve cuanto cubre y cuanto paga el cliente. */
    @PostMapping("/{ventaId}/convenio")
    public ResponseEntity<CopagoVista> convenio(@PathVariable UUID ventaId,
            @Valid @RequestBody ConvenioPeticion p) {
        var r = armar.aplicarConvenio(ventaId, p.convenioId());
        return ResponseEntity.ok(new CopagoVista(r.montoCubierto(), r.copago()));
    }

    /** Promociones que aplican a una linea concreta. Degradable: si no hay, devuelve vacio. */
    @GetMapping("/{ventaId}/promociones-disponibles")
    public ResponseEntity<List<AplicarPromocionesUseCase.Disponible>> promocionesDisponibles(
            @PathVariable UUID ventaId,
            @RequestParam(required = false) UUID detalleVentaId) {
        return ResponseEntity.ok(promociones.disponibles(ventaId, detalleVentaId));
    }

    /** Aplicar una promocion a una linea. El monto lo decide promociones, no la pantalla. */
    @PatchMapping("/{ventaId}/detalles/{detalleId}/promocion")
    public ResponseEntity<VentaVista> aplicarPromocion(@PathVariable UUID ventaId,
            @PathVariable UUID detalleId, @Valid @RequestBody PromocionPeticion p) {
        return ResponseEntity.ok(vista(promociones.aplicar(ventaId, detalleId, p.promocionId())));
    }

    @GetMapping("/{ventaId}")
    public ResponseEntity<VentaVista> obtener(@PathVariable UUID ventaId) {
        return ResponseEntity.ok(vista(armar.obtener(ventaId)));
    }

    private VentaVista vista(Venta v) {
        return new VentaVista(v.id(), v.estado().name(), v.clienteId(),
                v.subtotal(), v.descuento(), v.impuesto(), v.total(),
                armar.totalPagado(v.id()),
                null,
                v.lineas().stream()
                        .map(l -> new DetalleVista(l.id(), l.productoId(), l.nombreProducto(),
                                l.cantidad(), l.precioUnitario(), l.descuento(),
                                l.impuesto(), l.total()))
                        .toList());
    }
}

package com.posfarmacia.inventario.adapters.web;

import com.posfarmacia.contracts.api.ReservaRespuesta;
import com.posfarmacia.contracts.api.ReservaSolicitud;
import com.posfarmacia.inventario.usecases.port.out.StockPort;
import com.posfarmacia.inventario.usecases.usecase.ReservarStockUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El endpoint mas critico del sistema despues de confirmar venta.
 *
 * <p>Presupuesto de latencia: 500 ms desde ms-ventas. Si esto se pone lento, las cajas
 * se ponen lentas, y no hay degradacion posible: sin saber si hay stock no se vende.
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservasController {

    private final ReservarStockUseCase reservar;
    private final StockPort stock;

    public ReservasController(ReservarStockUseCase reservar, StockPort stock) {
        this.reservar = reservar;
        this.stock = stock;
    }

    /**
     * Aparta stock. Idempotente por (ventaId, productoId): el reintento del POS
     * devuelve la misma reserva, no aparta el doble.
     *
     * <p>Devuelve 200 con reservada=false cuando no alcanza, no un 409. Que un producto
     * se agote es negocio normal, y el cajero necesita el numero de lo que queda para
     * ofrecerselo al cliente sin tener que hacer otra consulta.
     */
    @PostMapping
    public ResponseEntity<ReservaRespuesta> reservar(@Valid @RequestBody ReservaSolicitud solicitud) {
        return ResponseEntity.ok(reservar.reservar(solicitud));
    }

    /** Liberar una reserva, por ejemplo cuando el cajero quita la linea de la venta. */
    @DeleteMapping("/{reservaId}")
    public ResponseEntity<Void> liberar(@PathVariable UUID reservaId) {
        reservar.liberar(reservaId);
        return ResponseEntity.noContent().build();
    }

    /** Disponible real. Tolera segundos de retraso, asi que el cliente lo puede cachear. */
    @GetMapping("/disponible")
    public ResponseEntity<Integer> disponible(@RequestParam UUID productoId,
            @RequestParam UUID localId) {
        return ResponseEntity.ok(stock.disponibleReal(productoId, localId));
    }
}

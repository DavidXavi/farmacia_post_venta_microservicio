package com.posfarmacia.credito.adapters.web;

import com.posfarmacia.contracts.api.CargoCreditoRespuesta;
import com.posfarmacia.contracts.api.CargoCreditoSolicitud;
import com.posfarmacia.credito.usecases.port.out.CreditoPort;
import com.posfarmacia.credito.usecases.usecase.ReservarCreditoUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/creditos")
public class CreditosController {

    private final ReservarCreditoUseCase reservar;
    private final CreditoPort credito;

    public CreditosController(ReservarCreditoUseCase reservar, CreditoPort credito) {
        this.reservar = reservar;
        this.credito = credito;
    }

    /** Presupuesto: 800 ms desde ms-ventas. Critica: si falla, no hay pago a credito. */
    @PostMapping("/reservas")
    public ResponseEntity<CargoCreditoRespuesta> reservar(
            @Valid @RequestBody CargoCreditoSolicitud solicitud) {
        return ResponseEntity.ok(reservar.reservar(solicitud));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<CreditoPort.Linea> linea(@PathVariable UUID clienteId) {
        return credito.lineaDe(clienteId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

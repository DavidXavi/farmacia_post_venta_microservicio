package com.posfarmacia.promociones.adapters.web;

import com.posfarmacia.contracts.api.EvaluarPromocionesRespuesta;
import com.posfarmacia.contracts.api.EvaluarPromocionesSolicitud;
import com.posfarmacia.promociones.usecases.usecase.EvaluarPromocionesUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El endpoint mas llamado del sistema despues del catalogo.
 *
 * <p>Presupuesto: 300 ms desde ms-ventas. Si no responde a tiempo, ms-ventas registra
 * la degradacion y sigue sin descuentos. Ese comportamiento es lo que justifica que
 * este servicio exista por separado: su caida cuesta descuentos, no ventas.
 */
@RestController
@RequestMapping("/api/promociones")
public class PromocionesController {

    private final EvaluarPromocionesUseCase evaluar;

    public PromocionesController(EvaluarPromocionesUseCase evaluar) {
        this.evaluar = evaluar;
    }

    @PostMapping("/evaluar")
    public ResponseEntity<EvaluarPromocionesRespuesta> evaluar(
            @Valid @RequestBody EvaluarPromocionesSolicitud solicitud) {
        return ResponseEntity.ok(evaluar.evaluar(solicitud));
    }
}

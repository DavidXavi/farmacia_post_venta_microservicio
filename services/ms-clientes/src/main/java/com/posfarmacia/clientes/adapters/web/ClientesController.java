package com.posfarmacia.clientes.adapters.web;

import com.posfarmacia.clientes.usecases.port.out.ClientePort;
import com.posfarmacia.contracts.api.ClienteDto;
import com.posfarmacia.contracts.api.CoberturaDto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de cliente desde la caja.
 *
 * <p>Presupuesto: 300 ms desde ms-ventas. Es degradable: si no responde, la venta sigue
 * como anonima. Negarse a cobrar porque no se pudo identificar al cliente le hace
 * perder la venta a la botica, y el cliente igual se lleva su producto.
 */
@RestController
@RequestMapping("/api/clientes")
public class ClientesController {

    private final ClientePort clientes;

    public ClientesController(ClientePort clientes) {
        this.clientes = clientes;
    }

    @GetMapping("/por-dni/{dni}")
    public ResponseEntity<ClienteDto> porDni(@PathVariable String dni) {
        return clientes.porDni(dni)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteDto> porId(@PathVariable UUID id) {
        return clientes.porId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Coberturas en lote: una venta de cinco lineas no puede ser cinco llamadas. */
    @GetMapping("/coberturas")
    public ResponseEntity<List<CoberturaDto>> coberturas(@RequestParam UUID convenioId,
            @RequestParam List<UUID> productoIds) {
        return ResponseEntity.ok(clientes.coberturas(convenioId, productoIds));
    }
}

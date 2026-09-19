package com.posfarmacia.catalogo.adapters.web;

import com.posfarmacia.catalogo.usecases.usecase.ConsultarCatalogoUseCase;
import com.posfarmacia.contracts.api.ProductoDto;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/productos")
public class ProductosController {

    public record CambioPrecio(BigDecimal precioVenta) {
    }

    private final ConsultarCatalogoUseCase catalogo;

    public ProductosController(ConsultarCatalogoUseCase catalogo) {
        this.catalogo = catalogo;
    }

    /**
     * Consulta en lote. Es el endpoint que usa ms-ventas y existe precisamente para
     * que una venta de cinco lineas sea UNA llamada y no cinco.
     */
    @GetMapping
    public ResponseEntity<List<ProductoDto>> porIds(@RequestParam(required = false) List<UUID> ids,
            @RequestParam(required = false) String buscar) {
        if (ids != null && !ids.isEmpty()) {
            return ResponseEntity.ok(catalogo.porIds(ids));
        }
        if (buscar != null && !buscar.isBlank()) {
            return ResponseEntity.ok(catalogo.buscar(buscar, 50));
        }
        // Sin filtro: el listado completo para la pantalla de productos. El limite
        // evita que un catalogo grande se sirva entero de una sola vez.
        return ResponseEntity.ok(catalogo.listar(500));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoDto> porId(@PathVariable UUID id) {
        ProductoDto p = catalogo.porId(id);
        return p == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(p);
    }

    @GetMapping("/por-barras/{codigo}")
    public ResponseEntity<ProductoDto> porBarras(@PathVariable String codigo) {
        ProductoDto p = catalogo.porCodigoBarras(codigo);
        return p == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(p);
    }

    @PutMapping("/{id}/precio")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR_CENTRAL')")
    public ResponseEntity<Void> cambiarPrecio(@PathVariable UUID id, @Valid @RequestBody CambioPrecio p) {
        catalogo.cambiarPrecio(id, p.precioVenta());
        return ResponseEntity.noContent().build();
    }
}

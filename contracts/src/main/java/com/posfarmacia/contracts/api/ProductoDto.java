package com.posfarmacia.contracts.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Lo que ms-catalogo expone a los demas. Es el objeto mas cacheado del sistema. */
public record ProductoDto(
        UUID id,
        String codigoInterno,
        String codigoBarras,
        String nombreComercial,
        UUID categoriaId,
        String categoriaNombre,
        BigDecimal precioVenta,
        BigDecimal tasaImpuesto,
        boolean esControlado,
        boolean requiereReceta,
        String tipoRecetaRequerida,
        String estado) {
}

package com.posfarmacia.catalogo.usecases.port.out;

import com.posfarmacia.contracts.api.ProductoDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductoPort {

    Optional<ProductoDto> porId(UUID id);

    List<ProductoDto> porIds(List<UUID> ids);

    Optional<ProductoDto> porCodigoBarras(String codigo);

    /** Busqueda por nombre desde la caja. Usa el indice trigram, no un LIKE sin indice. */
    List<ProductoDto> buscarPorNombre(String texto, int limite);

    List<ProductoDto> listar(int limite);

    void actualizarPrecio(UUID id, java.math.BigDecimal nuevoPrecio);
}

package com.posfarmacia.clientes.usecases.port.out;

import com.posfarmacia.contracts.api.ClienteDto;
import com.posfarmacia.contracts.api.CoberturaDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientePort {

    /** Cliente con sus convenios vigentes en UNA consulta: el POS los pide juntos. */
    Optional<ClienteDto> porDni(String dni);

    Optional<ClienteDto> porId(UUID id);

    /** Cobertura del convenio para varios productos de una vez, no de a uno. */
    List<CoberturaDto> coberturas(UUID convenioId, List<UUID> productoIds);
}

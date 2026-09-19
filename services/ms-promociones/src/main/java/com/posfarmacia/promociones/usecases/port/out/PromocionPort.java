package com.posfarmacia.promociones.usecases.port.out;

import com.posfarmacia.promociones.domain.ReglaPromocion;
import java.util.List;
import java.util.UUID;

public interface PromocionPort {

    /** Promociones vigentes hoy que tocan alguno de estos productos. */
    List<ReglaPromocion> vigentesPara(List<UUID> productoIds);
}

package com.posfarmacia.ventas.usecases.port.out;

import com.posfarmacia.contracts.api.CargoCreditoRespuesta;
import com.posfarmacia.contracts.api.CargoCreditoSolicitud;
import com.posfarmacia.contracts.api.ClienteDto;
import com.posfarmacia.contracts.api.CoberturaDto;
import com.posfarmacia.contracts.api.EvaluarPromocionesRespuesta;
import com.posfarmacia.contracts.api.EvaluarPromocionesSolicitud;
import com.posfarmacia.contracts.api.ProductoDto;
import com.posfarmacia.contracts.api.ReservaRespuesta;
import com.posfarmacia.contracts.api.ReservaSolicitud;
import java.util.List;
import java.util.UUID;

/**
 * Lo que ms-ventas necesita de los otros servicios, dicho en sus propios terminos.
 *
 * <p>Existe por la regla de dependencia: los casos de uso apuntan hacia adentro, asi
 * que no pueden nombrar al adaptador HTTP. Antes lo hacian, y el resultado practico
 * era que probar un caso de uso obligaba a levantar clientes HTTP, timeouts y circuit
 * breakers para comprobar una suma.
 *
 * <p>Los metodos estan agrupados por lo que pasa cuando fallan, que es la unica
 * division que importa aqui:
 *
 * <ul>
 *   <li><b>Criticas</b>: stock y credito. Si no responden, la venta no sigue.</li>
 *   <li><b>Degradables</b>: promociones, coberturas y datos del cliente. Si no
 *       responden, la venta sigue con menos informacion.</li>
 * </ul>
 *
 * <p>El catalogo queda en el medio: sin precio no se puede cotizar, pero se cachea, asi
 * que en la practica falla mucho menos que los otros.
 */
public interface ServiciosExternosPort {

    // --- criticas: si fallan, la venta no puede continuar -------------------

    ReservaRespuesta reservarStock(ReservaSolicitud solicitud);

    CargoCreditoRespuesta reservarCredito(CargoCreditoSolicitud solicitud);

    /** Productos en lote, nunca de a uno: el fan-out es lo que mata bajo carga. */
    List<ProductoDto> productos(List<UUID> ids);

    // --- degradables: si fallan, la venta sigue con menos informacion -------

    EvaluarPromocionesRespuesta evaluarPromociones(EvaluarPromocionesSolicitud solicitud);

    List<CoberturaDto> coberturas(UUID convenioId, List<UUID> productoIds);

    ClienteDto clientePorDni(String dni);
}

package com.posfarmacia.promociones.usecases.usecase;

import com.posfarmacia.contracts.api.EvaluarPromocionesRespuesta;
import com.posfarmacia.contracts.api.EvaluarPromocionesSolicitud;
import com.posfarmacia.promociones.domain.SelectorPromocion;
import com.posfarmacia.promociones.usecases.port.out.PromocionPort;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Evalua las promociones de una venta completa en una sola pasada.
 *
 * <p>Recibe todas las lineas juntas porque el POS las manda juntas, y eso no es un
 * detalle: pedirlas de a una convertiria una venta de cinco lineas en cinco llamadas
 * de red. A 200 ventas/s son 1000 req/s contra este servicio solo por fan-out evitable.
 *
 * <p>Una sola consulta a la base para todos los productos, y el resto es CPU. Por eso
 * este servicio se escala con replicas y no con una base mas grande.
 */
@Service
public class EvaluarPromocionesUseCase {

    private final PromocionPort promociones;
    private final Clock reloj;

    public EvaluarPromocionesUseCase(PromocionPort promociones, Clock reloj) {
        this.promociones = promociones;
        this.reloj = reloj;
    }

    public EvaluarPromocionesRespuesta evaluar(EvaluarPromocionesSolicitud solicitud) {
        LocalDate hoy = LocalDate.now(reloj);
        boolean hayCliente = solicitud.clienteId() != null;

        var productoIds = solicitud.lineas().stream()
                .map(EvaluarPromocionesSolicitud.Linea::productoId)
                .distinct()
                .toList();

        var reglas = promociones.vigentesPara(productoIds);
        var aplicables = new ArrayList<EvaluarPromocionesRespuesta.PromocionAplicable>();

        for (var linea : solicitud.lineas()) {
            SelectorPromocion.mejorPara(reglas, linea.productoId(), linea.cantidad(),
                            linea.precioUnitario(), hayCliente, hoy)
                    .filter(e -> e.descuento().signum() > 0)
                    .ifPresent(e -> aplicables.add(
                            new EvaluarPromocionesRespuesta.PromocionAplicable(
                                    linea.productoId(), e.promocionId(), e.nombre(),
                                    e.tipoBeneficio(), e.descuento())));
        }
        return new EvaluarPromocionesRespuesta(List.copyOf(aplicables));
    }
}

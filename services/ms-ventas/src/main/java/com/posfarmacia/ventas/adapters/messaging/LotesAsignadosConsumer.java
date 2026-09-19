package com.posfarmacia.ventas.adapters.messaging;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.posfarmacia.contracts.Sobre;
import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.LotesAsignados;
import com.posfarmacia.plataforma.idempotencia.ConsumoIdempotente;
import com.posfarmacia.ventas.usecases.port.out.SagaPort;
import com.posfarmacia.ventas.usecases.port.out.VentaPort;
import com.posfarmacia.ventas.usecases.usecase.ConfirmarVentaUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recibe de ms-inventario que lotes salieron para cada venta y guarda la copia que se
 * imprime en el comprobante.
 *
 * <p>La verdad de la trazabilidad se queda en pg_inventario. Esta copia existe solo
 * para que imprimir una boleta de hace un ano no dependa de que ms-inventario este vivo.
 */
@Component
public class LotesAsignadosConsumer {

    private static final String CONSUMIDOR = "ms-ventas.lotes-asignados";

    private final ObjectMapper json;
    private final ConsumoIdempotente idempotencia;
    private final VentaPort ventas;
    private final ConfirmarVentaUseCase saga;

    public LotesAsignadosConsumer(ObjectMapper json, ConsumoIdempotente idempotencia,
            VentaPort ventas, ConfirmarVentaUseCase saga) {
        this.json = json;
        this.idempotencia = idempotencia;
        this.ventas = ventas;
        this.saga = saga;
    }

    @KafkaListener(topics = Topicos.LOTES_ASIGNADOS, groupId = "ms-ventas")
    @Transactional
    public void recibir(String mensaje, Acknowledgment ack) throws Exception {
        Sobre<LotesAsignados> sobre = json.readValue(mensaje, new TypeReference<>() {
        });

        idempotencia.unaVez(sobre.eventoId(), CONSUMIDOR, () -> {
            LotesAsignados datos = sobre.datos();
            datos.asignaciones().forEach(a -> ventas.guardarLotesAsignados(
                    datos.ventaId(), a.productoId(), a.loteId(), a.cantidad()));
            saga.marcarPaso(datos.ventaId(), SagaPort.Paso.STOCK);
        });
        ack.acknowledge();
    }
}

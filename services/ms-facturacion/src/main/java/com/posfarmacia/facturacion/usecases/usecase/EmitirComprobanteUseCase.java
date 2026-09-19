package com.posfarmacia.facturacion.usecases.usecase;

import com.posfarmacia.contracts.eventos.VentaConfirmada;
import com.posfarmacia.facturacion.usecases.port.out.ComprobantePort;
import com.posfarmacia.facturacion.usecases.port.out.SunatPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emisión de comprobantes electrónicos.
 *
 * <p>Este es el servicio que justifica el patrón completo. SUNAT se cae, y se cae
 * seguido. Si la emisión estuviera dentro de ms-ventas, cada caída de SUNAT sería una
 * caída de todas las cajas de la cadena.
 *
 * <p>Aquí pasa en dos tiempos:
 * <ol>
 *   <li>Al recibir VentaConfirmada se GUARDA el comprobante con estado PENDIENTE y se
 *       le asigna su correlativo. Rápido, local, sin salir a internet.</li>
 *   <li>Un job envía los pendientes a SUNAT con reintentos. Puede tardar segundos o
 *       hasta el día siguiente. La caja ya cerró hace rato.</li>
 * </ol>
 *
 * <p>La llamada a SUNAT queda FUERA de cualquier transacción: sostener una conexión de
 * base de datos durante 15 segundos esperando a un tercero lento es exactamente el
 * problema que esta arquitectura existe para evitar. Guardar el resultado sí es
 * transaccional, y por eso vive en {@link RegistrarEnvioUseCase}, que es un bean
 * aparte: una llamada entre métodos del mismo bean no pasa por el proxy de Spring y
 * la anotación no se aplicaría.
 */
@Service
public class EmitirComprobanteUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmitirComprobanteUseCase.class);

    private final ComprobantePort comprobantes;
    private final SunatPort sunat;
    private final RegistrarEnvioUseCase registrarEnvio;
    private final AtomicInteger pendientes = new AtomicInteger();

    public EmitirComprobanteUseCase(ComprobantePort comprobantes, SunatPort sunat,
            RegistrarEnvioUseCase registrarEnvio, MeterRegistry metricas) {
        this.comprobantes = comprobantes;
        this.sunat = sunat;
        this.registrarEnvio = registrarEnvio;
        metricas.gauge("comprobantes.pendientes.total", pendientes, AtomicInteger::get);
    }

    /**
     * Paso 1: registrar el comprobante. Local y rápido.
     *
     * <p>El correlativo sale de un {@code UPDATE ... RETURNING} sobre la tabla de
     * series, no de un contador en memoria: la numeración ante SUNAT no admite huecos
     * ni repetidos, y con seis réplicas del servicio un contador en memoria los
     * produciría el primer día.
     */
    @Transactional
    public UUID registrar(VentaConfirmada venta) {
        String tipo = venta.tipoComprobante() == null ? "BOLETA" : venta.tipoComprobante();
        String serie = "FACTURA".equals(tipo) ? "F001" : "B001";
        int correlativo = comprobantes.siguienteCorrelativo(serie);

        UUID id = comprobantes.guardarPendiente(venta.ventaId(), venta.localId(), tipo,
                serie, correlativo, venta.total(), venta.fecha());

        log.info("Comprobante {}-{} registrado para la venta {}. Envío a SUNAT en segundo plano.",
                serie, correlativo, venta.ventaId());
        return id;
    }

    /**
     * Paso 2: enviar los pendientes a SUNAT.
     *
     * <p>ponytail: un job cada 5 s con lotes de 50, no una cola de trabajos con workers
     * dedicados. La cola ya existe y es la tabla: los pendientes están en un índice
     * parcial y se toman con {@code SKIP LOCKED}, así que varias réplicas se reparten
     * el trabajo solas. El techo: si SUNAT aceptara más de 10 envíos por segundo y
     * hubiera cola acumulada, habría que subir el lote o el paralelismo, y eso es un
     * número en configuración, no un rediseño.
     */
    @Scheduled(fixedDelayString = "${pos.sunat.intervalo-ms:5000}")
    public void enviarPendientes() {
        var lote = comprobantes.pendientes(50);
        pendientes.set(lote.size());
        if (lote.isEmpty()) {
            return;
        }

        for (var c : lote) {
            try {
                var respuesta = sunat.enviar(c.id(), c.tipo(), c.serie(), c.correlativo(),
                        c.montoTotal());
                registrarEnvio.registrar(c, respuesta);
            } catch (RuntimeException e) {
                // Un comprobante que explota no puede arrastrar a los otros 49 del lote.
                // Sigue PENDIENTE y el próximo ciclo lo reintenta.
                log.error("Fallo al procesar el comprobante {}-{}: {}",
                        c.serie(), c.correlativo(), e.getMessage());
            }
        }
    }
}

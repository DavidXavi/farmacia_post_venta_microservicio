package com.posfarmacia.contracts;

/**
 * Nombres de topicos y colas. Constantes, no configuracion: un typo en un topico no
 * falla, simplemente el mensaje se va a un topico que nadie lee y el error aparece
 * dias despues en un reporte que no cuadra.
 *
 * <p>Todos los topicos usan {@code localId} como clave de particion. Eso garantiza
 * orden por local (las ventas de una misma botica se procesan en orden) y da
 * paralelismo natural entre locales, que es el shard real del negocio.
 */
public final class Topicos {

    private Topicos() {
    }

    /** Venta ya confirmada. La consumen inventario, credito, facturacion y reportes. */
    public static final String VENTAS_CONFIRMADAS = "pos.ventas.confirmadas";

    /** Venta anulada. Dispara la compensacion en inventario y credito. */
    public static final String VENTAS_ANULADAS = "pos.ventas.anuladas";

    /** Resultado del FEFO: que lotes salieron para que venta. Lo consume ventas. */
    public static final String LOTES_ASIGNADOS = "pos.stock.lotes-asignados";

    /** Movimientos de inventario, para el read model de reportes. */
    public static final String STOCK_MOVIMIENTOS = "pos.stock.movimientos";

    /** Cambio en catalogo: invalida la cache de los dos niveles en todos los pods. */
    public static final String CATALOGO_CAMBIOS = "pos.catalogo.cambios";

    /** Comprobante aceptado por SUNAT. Cierra la saga en ventas. */
    public static final String COMPROBANTES_EMITIDOS = "pos.comprobantes.emitidos";

    /** Auditoria transversal: cualquier servicio publica, identidad consume. */
    public static final String AUDITORIA = "pos.auditoria";

    /** Cola RabbitMQ: emision de comprobante. Trabajo dirigido a un solo consumidor. */
    public static final String COLA_EMITIR_COMPROBANTE = "pos.comprobantes.emitir";

    /** Exchange y routing key de la cola anterior. */
    public static final String EXCHANGE_COMPROBANTES = "pos.comprobantes";
    public static final String RK_EMITIR = "emitir";

    /**
     * Sufijos de la cadena de reintento y la cola muerta. Un consumidor que falla no
     * bloquea la particion: el mensaje salta al topico de reintento siguiente y, si
     * agota los tres, cae en la DLQ y suena una alerta.
     */
    public static final String[] REINTENTOS = {".retry.5s", ".retry.1m", ".retry.10m"};
    public static final String DLQ = ".dlq";
}

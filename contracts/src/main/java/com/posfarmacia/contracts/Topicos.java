package com.posfarmacia.contracts;

/**
 * Nombres de topicos de Kafka. Constantes, no configuracion: un typo en un topico no
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

    /** Cambio en catalogo: permite invalidar la cache sin esperar al TTL. */
    public static final String CATALOGO_CAMBIOS = "pos.catalogo.cambios";

    /** Comprobante aceptado por SUNAT. Cierra la saga en ventas. */
    public static final String COMPROBANTES_EMITIDOS = "pos.comprobantes.emitidos";

    /** Auditoria transversal: cualquier servicio publica, identidad consume. */
    public static final String AUDITORIA = "pos.auditoria";

    /**
     * Sufijo de la cola muerta. Un evento que sigue fallando despues de los reintentos
     * se guarda en {@code <topico>.dlq} en vez de descartarse: queda ahi para revisarlo
     * y volver a publicarlo.
     */
    public static final String DLQ = ".dlq";
}

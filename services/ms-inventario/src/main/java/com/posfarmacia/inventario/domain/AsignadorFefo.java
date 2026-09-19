package com.posfarmacia.inventario.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * FEFO: first expired, first out. Sale primero el lote que vence antes.
 *
 * <p>En farmacia esto no es una optimizacion de almacen, es obligatorio. Despachar un
 * lote con vencimiento lejano teniendo uno proximo garantiza que el proximo se venza
 * en el anaquel, y un medicamento vencido en stock es una observacion de DIGEMID.
 *
 * <p>Se ejecuta al CONFIRMAR la venta, en el consumidor del evento, no cuando el
 * cajero agrega el producto. Dos razones: el cajero no necesita saber de que lote
 * sale hasta el despacho, y meter esta escritura en el camino critico agregaria
 * contencion justo donde ya hay.
 *
 * <p>Logica de dominio pura: sin Spring, sin base de datos, sin anotaciones. Por eso
 * se puede probar con un test de tres lineas y sin levantar nada.
 */
public final class AsignadorFefo {

    private AsignadorFefo() {
    }

    /**
     * Reparte {@code cantidad} entre los lotes disponibles, del que vence antes al que
     * vence despues.
     *
     * @throws StockInsuficienteException si los lotes despachables no cubren la cantidad.
     *         Puede pasar aunque el contador diga que si hay: el contador no sabe de
     *         vencimientos, y un lote que vencio ayer sigue sumando hasta que alguien
     *         lo da de baja. Que las dos cosas puedan discrepar es el precio de tener
     *         un contador rapido; que la discrepancia se detecte aca es lo que evita
     *         que se despache un vencido.
     */
    public static List<AsignacionLote> asignar(List<Lote> lotes, int cantidad, LocalDate hoy) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a asignar debe ser mayor que cero");
        }

        List<Lote> candidatos = lotes.stream()
                .filter(l -> l.despachable(hoy))
                .sorted(Comparator.comparing(Lote::fechaVencimiento)
                        // Desempate por id: dos lotes con el mismo vencimiento deben
                        // asignarse siempre en el mismo orden, o dos ejecuciones del
                        // mismo evento producirian asignaciones distintas.
                        .thenComparing(Lote::id))
                .toList();

        List<AsignacionLote> asignaciones = new ArrayList<>();
        int porCubrir = cantidad;

        for (Lote lote : candidatos) {
            if (porCubrir == 0) {
                break;
            }
            int toma = Math.min(porCubrir, lote.cantidadDisponible());
            asignaciones.add(new AsignacionLote(
                    lote.id(), lote.codigo(), lote.fechaVencimiento(), toma));
            porCubrir -= toma;
        }

        if (porCubrir > 0) {
            UUID producto = candidatos.isEmpty() ? null : candidatos.get(0).productoId();
            throw new StockInsuficienteException(producto, cantidad, cantidad - porCubrir);
        }
        return asignaciones;
    }
}

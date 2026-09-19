package com.posfarmacia.ventas.usecases.usecase;

import java.util.UUID;

/**
 * Resuelve los nombres que el evento VentaConfirmada lleva adentro aunque ms-ventas no
 * sea dueno de ellos: nombre del local, del vendedor, del cliente y de la categoria.
 *
 * <p>Es una interfaz y no una llamada directa a los otros servicios porque estos datos
 * cambian una vez al ano. La implementacion los cachea y, si el servicio de origen no
 * responde, devuelve lo que tenga o null: el nombre del local no puede impedir que se
 * confirme una venta.
 */
public interface DatosDenormalizados {

    record Categoria(UUID id, String nombre) {
    }

    /**
     * Guarda el nombre del cliente que el caso de uso acaba de resolver.
     *
     * <p>Aprender de lo que ya se consulto sale gratis: el cajero identifica al cliente
     * una vez y el nombre queda listo para el evento, sin una segunda llamada.
     */
    void recordarCliente(UUID clienteId, String nombre);

    /** Idem para la categoria, que se aprende del producto consultado al escanear. */
    void recordarCategoria(UUID productoId, UUID categoriaId, String categoriaNombre);

    String nombreLocal(UUID localId);

    String nombreUsuario(UUID usuarioId);

    String nombreCliente(UUID clienteId);

    Categoria categoriaDe(UUID productoId);
}

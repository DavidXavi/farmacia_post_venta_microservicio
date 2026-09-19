package com.posfarmacia.ventas.domain;

/**
 * Se intento algo que la venta no permite.
 *
 * <p>Cuelga de {@code IllegalArgumentException} para que el manejador de errores la
 * traduzca a 400 con su mensaje. Es un tipo del JDK: el dominio sigue sin depender de
 * nada.
 */
public class VentaInvalidaException extends IllegalArgumentException {

    public VentaInvalidaException(String mensaje) {
        super(mensaje);
    }
}

package com.posfarmacia.plataforma.web;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de negocio a codigos HTTP con mensaje.
 *
 * <p>Sin esto, "el producto requiere receta" y "solo quedan 3 unidades" salen como 500
 * sin cuerpo: el cajero ve "error interno" y no sabe que hacer, y el monitoreo cuenta
 * como caida del servicio lo que fue una regla de negocio cumpliendose. Un 500 tiene
 * que significar que el sistema fallo, no que el cliente pidio algo que no se puede.
 *
 * <p>Se apoya en los tipos del JDK a proposito: {@code IllegalArgumentException} para
 * lo que nunca fue valido y {@code IllegalStateException} para lo que no se puede en
 * este estado. Asi el dominio no importa nada de plataforma, que es la regla de
 * dependencia del proyecto, y esta clase no nombra ninguna entidad.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    /** Lo que se pidio nunca fue valido: cantidad cero, producto que no existe, falta la receta. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail peticionInvalida(IllegalArgumentException e) {
        return problema(HttpStatus.BAD_REQUEST, e);
    }

    /** Se pudo pedir, pero no en este estado: sin stock, venta ya confirmada, caja cerrada. */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail conflicto(IllegalStateException e) {
        return problema(HttpStatus.CONFLICT, e);
    }

    /**
     * Violacion de {@code @Valid}: dice que campo y por que.
     *
     * <p>El 400 por defecto de Spring viaja sin cuerpo util, asi que el cajero ve
     * "Error 400" y no sabe si escribio mal la cantidad o falta el producto.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail campoInvalido(MethodArgumentNotValidException e) {
        String campos = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Peticion invalida -> 400: {}", campos);
        var detalle = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detalle.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        detalle.setDetail(campos.isBlank() ? "La peticion no es valida" : campos);
        return detalle;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail noEncontrado(NoSuchElementException e) {
        return problema(HttpStatus.NOT_FOUND, e);
    }

    private ProblemDetail problema(HttpStatus estado, RuntimeException e) {
        // WARN y no ERROR: es el sistema funcionando. Pero se registra igual, porque si
        // un fallo de programacion viaja como IllegalArgumentException, el log es el
        // unico lugar donde se nota.
        log.warn("{} -> {}: {}", e.getClass().getSimpleName(), estado.value(), e.getMessage());
        var detalle = ProblemDetail.forStatus(estado);
        detalle.setTitle(estado.getReasonPhrase());
        detalle.setDetail(e.getMessage());
        return detalle;
    }
}

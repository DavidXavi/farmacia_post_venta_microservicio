package com.posfarmacia.catalogo.usecases.usecase;

import com.posfarmacia.catalogo.usecases.port.out.CatalogoEscrituraPort;
import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.CatalogoCambiado;
import com.posfarmacia.plataforma.outbox.OutboxRegistrador;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Altas del catalogo: producto, categoria, laboratorio y presentacion.
 *
 * <p>Las cuatro estan juntas porque comparten el mismo par de reglas (no duplicar y no
 * apuntar a algo que no existe) y la misma transaccion. Cuatro clases de quince lineas
 * cada una serian cuatro archivos para escribir el mismo INSERT.
 *
 * <p>El alta de un producto publica {@code CatalogoCambiado} por outbox. No es por
 * simetria: los otros servicios cachean catalogo, y un producto nuevo que no invalida
 * caches tarda hasta diez minutos en poder venderse. Diez minutos con el cliente
 * preguntando por que el sistema no encuentra algo que esta en el anaquel.
 */
@Service
public class RegistrarCatalogoUseCase {

    private final CatalogoEscrituraPort escritura;
    private final OutboxRegistrador outbox;

    public RegistrarCatalogoUseCase(CatalogoEscrituraPort escritura, OutboxRegistrador outbox) {
        this.escritura = escritura;
        this.outbox = outbox;
    }

    /** Lo que llega de la pantalla, antes de convertirse en producto. */
    public record AltaProducto(
            String codigoInterno,
            String codigoBarras,
            String nombreComercial,
            String descripcion,
            String tipoProducto,
            UUID categoriaId,
            UUID laboratorioId,
            UUID presentacionId,
            BigDecimal precioVenta,
            boolean esControlado,
            boolean requiereReceta,
            String tipoRecetaRequerida) {
    }

    @Transactional
    public UUID registrarProducto(AltaProducto alta) {
        if (escritura.existeCodigoInterno(alta.codigoInterno())) {
            throw new IllegalStateException(
                    "Ya existe un producto con el codigo " + alta.codigoInterno());
        }
        exigir(escritura.existeCategoria(alta.categoriaId()), "La categoria no existe");
        exigir(escritura.existeLaboratorio(alta.laboratorioId()), "El laboratorio no existe");
        exigir(escritura.existePresentacion(alta.presentacionId()), "La presentacion no existe");
        if (alta.precioVenta() == null || alta.precioVenta().signum() <= 0) {
            throw new IllegalArgumentException("El precio de venta tiene que ser mayor que cero");
        }

        // Un controlado sin receta es una carga mal hecha que despues deja vender un
        // psicotropico sin papel. Se corrige en vez de rechazarse: el operador marco lo
        // importante y se olvido de la casilla de al lado.
        boolean requiereReceta = alta.requiereReceta() || alta.esControlado();
        String tipoReceta = requiereReceta
                ? (alta.tipoRecetaRequerida() == null ? "Normal" : alta.tipoRecetaRequerida())
                : null;

        UUID id = UUID.randomUUID();
        escritura.insertarProducto(new CatalogoEscrituraPort.NuevoProducto(
                id, alta.codigoInterno().trim(), vacioComoNulo(alta.codigoBarras()),
                alta.nombreComercial().trim(),
                alta.descripcion() == null ? "" : alta.descripcion().trim(),
                alta.tipoProducto(), alta.categoriaId(), alta.laboratorioId(),
                alta.presentacionId(), alta.precioVenta(), alta.esControlado(),
                requiereReceta, tipoReceta));

        outbox.registrar("producto", id, Topicos.CATALOGO_CAMBIOS, id,
                new CatalogoCambiado(id, "ALTA_PRODUCTO", Instant.now()));
        return id;
    }

    @Transactional
    public UUID registrarCategoria(String nombre) {
        UUID id = UUID.randomUUID();
        escritura.insertarCategoria(id, exigirNombre(nombre));
        return id;
    }

    @Transactional
    public UUID registrarLaboratorio(String nombre) {
        UUID id = UUID.randomUUID();
        escritura.insertarLaboratorio(id, exigirNombre(nombre));
        return id;
    }

    @Transactional
    public UUID registrarPresentacion(String nombre, String unidadMedida) {
        UUID id = UUID.randomUUID();
        escritura.insertarPresentacion(id, exigirNombre(nombre), exigirNombre(unidadMedida));
        return id;
    }

    private String exigirNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacio");
        }
        return nombre.trim();
    }

    private void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    /** Un código de barras vacío es "no tiene", no una cadena vacía que rompe el índice único. */
    private String vacioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}

package com.posfarmacia.catalogo.adapters.persistence;

import com.posfarmacia.catalogo.usecases.port.out.CatalogoEscrituraPort;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Altas del catalogo en SQL explicito, igual que el resto de la persistencia. */
@Repository
public class CatalogoEscrituraJdbcAdapter implements CatalogoEscrituraPort {

    private static final String INSERTAR_PRODUCTO = """
            INSERT INTO productos (id, codigo_interno, codigo_barras, nombre_comercial,
                                   descripcion, tipo_producto, categoria_id, laboratorio_id,
                                   presentacion_id, precio_venta, es_controlado,
                                   requiere_receta, tipo_receta_requerida, estado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVO')
            """;

    private final JdbcClient jdbc;

    public CatalogoEscrituraJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertarProducto(NuevoProducto p) {
        jdbc.sql(INSERTAR_PRODUCTO)
                .param(p.id()).param(p.codigoInterno()).param(p.codigoBarras())
                .param(p.nombreComercial()).param(p.descripcion()).param(p.tipoProducto())
                .param(p.categoriaId()).param(p.laboratorioId()).param(p.presentacionId())
                .param(p.precioVenta()).param(p.esControlado()).param(p.requiereReceta())
                .param(p.tipoRecetaRequerida())
                .update();
    }

    @Override
    public boolean existeCodigoInterno(String codigoInterno) {
        return existe("SELECT 1 FROM productos WHERE codigo_interno = ?", codigoInterno);
    }

    @Override
    public boolean existeCategoria(UUID id) {
        return existe("SELECT 1 FROM categorias WHERE id = ?", id);
    }

    @Override
    public boolean existeLaboratorio(UUID id) {
        return existe("SELECT 1 FROM laboratorios WHERE id = ?", id);
    }

    @Override
    public boolean existePresentacion(UUID id) {
        return existe("SELECT 1 FROM presentaciones WHERE id = ?", id);
    }

    @Override
    public void insertarCategoria(UUID id, String nombre) {
        jdbc.sql("INSERT INTO categorias (id, nombre) VALUES (?, ?)")
                .param(id).param(nombre).update();
    }

    @Override
    public void insertarLaboratorio(UUID id, String nombre) {
        jdbc.sql("INSERT INTO laboratorios (id, nombre) VALUES (?, ?)")
                .param(id).param(nombre).update();
    }

    @Override
    public void insertarPresentacion(UUID id, String nombre, String unidadMedida) {
        jdbc.sql("INSERT INTO presentaciones (id, nombre, unidad_medida) VALUES (?, ?, ?)")
                .param(id).param(nombre).param(unidadMedida).update();
    }

    private boolean existe(String sql, Object parametro) {
        return jdbc.sql(sql).param(parametro).query(Integer.class).optional().isPresent();
    }
}

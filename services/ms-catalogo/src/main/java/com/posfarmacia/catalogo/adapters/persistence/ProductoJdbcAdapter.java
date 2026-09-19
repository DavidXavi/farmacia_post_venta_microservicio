package com.posfarmacia.catalogo.adapters.persistence;

import com.posfarmacia.catalogo.usecases.port.out.ProductoPort;
import com.posfarmacia.contracts.api.ProductoDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProductoJdbcAdapter implements ProductoPort {

    /** El IGV no esta en la tabla: es regla tributaria, no atributo del producto. */
    private static final BigDecimal IGV = new BigDecimal("18.00");

    private static final String SELECT = """
            SELECT p.id, p.codigo_interno, p.codigo_barras, p.nombre_comercial,
                   p.categoria_id, c.nombre AS categoria_nombre, p.precio_venta,
                   p.es_controlado, p.requiere_receta, p.tipo_receta_requerida, p.estado
              FROM productos p
              JOIN categorias c ON c.id = p.categoria_id
            """;

    private static final RowMapper<ProductoDto> MAPPER = (rs, fila) -> new ProductoDto(
            rs.getObject("id", UUID.class),
            rs.getString("codigo_interno"),
            rs.getString("codigo_barras"),
            rs.getString("nombre_comercial"),
            rs.getObject("categoria_id", UUID.class),
            rs.getString("categoria_nombre"),
            rs.getBigDecimal("precio_venta"),
            IGV,
            rs.getBoolean("es_controlado"),
            rs.getBoolean("requiere_receta"),
            rs.getString("tipo_receta_requerida"),
            rs.getString("estado"));

    private final JdbcClient jdbc;

    public ProductoJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ProductoDto> porId(UUID id) {
        return jdbc.sql(SELECT + " WHERE p.id = ?").param(id).query(MAPPER).optional();
    }

    @Override
    public List<ProductoDto> porIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        // = ANY(?) con un array, no un IN con N parametros: el plan de Postgres se
        // cachea una sola vez en vez de uno distinto por cada tamano de lista.
        return jdbc.sql(SELECT + " WHERE p.id = ANY (?)")
                .param(ids.toArray(UUID[]::new))
                .query(MAPPER)
                .list();
    }

    @Override
    public Optional<ProductoDto> porCodigoBarras(String codigo) {
        return jdbc.sql(SELECT + " WHERE p.codigo_barras = ?")
                .param(codigo).query(MAPPER).optional();
    }

    @Override
    public List<ProductoDto> buscarPorNombre(String texto, int limite) {
        // El operador de similitud usa el indice GIN trigram. Un LIKE con comodin al
        // inicio no lo usaria y a 5000 req/s seria un escaneo secuencial por cada tecla
        // que pulsa el cajero.
        return jdbc.sql(SELECT + """
                 WHERE p.nombre_comercial ILIKE ('%' || ? || '%') AND p.estado = 'ACTIVO'
                 ORDER BY similarity(p.nombre_comercial, ?) DESC
                 LIMIT ?
                """)
                .param(texto).param(texto).param(limite)
                .query(MAPPER)
                .list();
    }

    @Override
    public List<ProductoDto> listar(int limite) {
        return jdbc.sql(SELECT + " ORDER BY p.nombre_comercial LIMIT ?")
                .param(limite).query(MAPPER).list();
    }

    @Override
    public void actualizarPrecio(UUID id, BigDecimal nuevoPrecio) {
        jdbc.sql("UPDATE productos SET precio_venta = ?, version = version + 1 WHERE id = ?")
                .param(nuevoPrecio).param(id)
                .update();
    }
}

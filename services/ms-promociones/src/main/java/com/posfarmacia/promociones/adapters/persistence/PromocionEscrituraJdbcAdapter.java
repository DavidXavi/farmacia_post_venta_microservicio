package com.posfarmacia.promociones.adapters.persistence;

import com.posfarmacia.promociones.usecases.port.out.PromocionEscrituraPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Altas y cambios de promociones, en SQL explicito. */
@Repository
public class PromocionEscrituraJdbcAdapter implements PromocionEscrituraPort {

    private static final String INSERTAR = """
            INSERT INTO promociones (id, nombre, descripcion, tipo_beneficio, valor_beneficio,
                                     requiere_cliente, cantidad_minima, vigencia_inicio,
                                     vigencia_fin, activa)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, true)
            """;

    private static final String ACTUALIZAR = """
            UPDATE promociones
               SET nombre = ?, descripcion = ?, tipo_beneficio = ?, valor_beneficio = ?,
                   requiere_cliente = ?, cantidad_minima = ?, vigencia_inicio = ?,
                   vigencia_fin = ?, version = version + 1
             WHERE id = ?
            """;

    private final JdbcClient jdbc;

    public PromocionEscrituraJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(UUID id, DatosPromocion d, List<UUID> productos) {
        jdbc.sql(INSERTAR)
                .param(id).param(d.nombre()).param(d.descripcion()).param(d.tipoBeneficio())
                .param(d.valorBeneficio()).param(d.requiereCliente()).param(d.cantidadMinima())
                .param(d.vigenciaInicio()).param(d.vigenciaFin())
                .update();
        reemplazarProductos(id, productos);
    }

    @Override
    public boolean actualizar(UUID id, DatosPromocion d, List<UUID> productos) {
        int filas = jdbc.sql(ACTUALIZAR)
                .param(d.nombre()).param(d.descripcion()).param(d.tipoBeneficio())
                .param(d.valorBeneficio()).param(d.requiereCliente()).param(d.cantidadMinima())
                .param(d.vigenciaInicio()).param(d.vigenciaFin()).param(id)
                .update();
        if (filas == 0) {
            return false;
        }
        reemplazarProductos(id, productos);
        return true;
    }

    @Override
    public Optional<Boolean> estaActiva(UUID id) {
        return jdbc.sql("SELECT activa FROM promociones WHERE id = ?")
                .param(id).query(Boolean.class).optional();
    }

    @Override
    public void desactivar(UUID id) {
        jdbc.sql("UPDATE promociones SET activa = false, version = version + 1 WHERE id = ?")
                .param(id).update();
    }

    /**
     * Borrar y reinsertar las condiciones: una promocion alcanza a un punado de
     * productos, no a miles, y reconciliar altas y bajas seria mas codigo para el mismo
     * resultado. Nada apunta a estas filas, asi que el borrado no arrastra nada.
     */
    private void reemplazarProductos(UUID promocionId, List<UUID> productos) {
        jdbc.sql("DELETE FROM promocion_condiciones WHERE promocion_id = ?")
                .param(promocionId).update();
        for (UUID producto : productos) {
            jdbc.sql("INSERT INTO promocion_condiciones (id, promocion_id, producto_id) VALUES (?, ?, ?)")
                    .param(UUID.randomUUID()).param(promocionId).param(producto)
                    .update();
        }
    }
}

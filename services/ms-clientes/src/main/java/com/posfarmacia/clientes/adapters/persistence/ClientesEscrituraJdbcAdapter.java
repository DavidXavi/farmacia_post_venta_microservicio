package com.posfarmacia.clientes.adapters.persistence;

import com.posfarmacia.clientes.usecases.port.out.ClientesEscrituraPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Altas y cambios de ms-clientes, en SQL explicito. */
@Repository
public class ClientesEscrituraJdbcAdapter implements ClientesEscrituraPort {

    private static final String INSERTAR_CLIENTE = """
            INSERT INTO clientes (id, dni, nombres, apellidos, fecha_nacimiento,
                                  telefono, correo, direccion, estado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVO')
            """;

    /**
     * COALESCE por campo: la pantalla de edicion manda solo lo que se toco, y un null
     * significa "no lo cambies", no "borralo". Sin esto, editar el telefono borraria
     * el correo.
     */
    private static final String ACTUALIZAR_CLIENTE = """
            UPDATE clientes
               SET nombres   = COALESCE(?, nombres),
                   apellidos = COALESCE(?, apellidos),
                   telefono  = COALESCE(?, telefono),
                   correo    = COALESCE(?, correo),
                   direccion = COALESCE(?, direccion),
                   estado    = COALESCE(?, estado)
             WHERE id = ?
            """;

    private static final String GUARDAR_COBERTURA = """
            INSERT INTO coberturas_seguro (id, convenio_id, producto_id, porcentaje_cubierto)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (convenio_id, producto_id) DO UPDATE
               SET porcentaje_cubierto = EXCLUDED.porcentaje_cubierto
            """;

    private static final String GUARDAR_AFILIACION = """
            INSERT INTO afiliaciones_cliente
                   (id, cliente_id, convenio_id, vigencia_inicio, vigencia_fin, estado)
            VALUES (?, ?, ?, ?, ?, 'ACTIVA')
            ON CONFLICT (cliente_id, convenio_id) DO UPDATE
               SET vigencia_inicio = EXCLUDED.vigencia_inicio,
                   vigencia_fin    = EXCLUDED.vigencia_fin,
                   estado          = 'ACTIVA'
            """;

    private static final String INSERTAR_RECETA = """
            INSERT INTO recetas (id, numero, tipo, fecha_emision, fecha_vencimiento,
                                 producto_id, cliente_id, datos_paciente, datos_profesional,
                                 dosis, cantidad_autorizada, archivo_respaldo_url, estado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE')
            """;

    private final JdbcClient jdbc;

    public ClientesEscrituraJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existeDni(String dni) {
        return existe("SELECT 1 FROM clientes WHERE dni = ?", dni);
    }

    @Override
    public boolean existeCliente(UUID id) {
        return existe("SELECT 1 FROM clientes WHERE id = ?", id);
    }

    @Override
    public void insertarCliente(NuevoCliente c) {
        jdbc.sql(INSERTAR_CLIENTE)
                .param(c.id()).param(c.dni()).param(c.nombres()).param(c.apellidos())
                .param(c.fechaNacimiento()).param(c.telefono()).param(c.correo())
                .param(c.direccion())
                .update();
    }

    @Override
    public boolean actualizarCliente(UUID id, CambioCliente c) {
        return jdbc.sql(ACTUALIZAR_CLIENTE)
                .param(c.nombres()).param(c.apellidos()).param(c.telefono())
                .param(c.correo()).param(c.direccion()).param(c.estado()).param(id)
                .update() == 1;
    }

    @Override
    public boolean existeConvenio(UUID id) {
        return existe("SELECT 1 FROM convenios_seguro WHERE id = ?", id);
    }

    @Override
    public void insertarConvenio(UUID id, String nombre) {
        jdbc.sql("INSERT INTO convenios_seguro (id, nombre, activo) VALUES (?, ?, true)")
                .param(id).param(nombre).update();
    }

    @Override
    public void guardarCobertura(UUID id, UUID convenioId, UUID productoId, BigDecimal porcentaje) {
        jdbc.sql(GUARDAR_COBERTURA)
                .param(id).param(convenioId).param(productoId).param(porcentaje).update();
    }

    @Override
    public void guardarAfiliacion(UUID id, UUID clienteId, UUID convenioId,
            LocalDate vigenciaInicio, LocalDate vigenciaFin) {
        jdbc.sql(GUARDAR_AFILIACION)
                .param(id).param(clienteId).param(convenioId)
                .param(vigenciaInicio).param(vigenciaFin)
                .update();
    }

    @Override
    public boolean existeNumeroReceta(String numero) {
        return existe("SELECT 1 FROM recetas WHERE numero = ?", numero);
    }

    @Override
    public void insertarReceta(NuevaReceta r) {
        jdbc.sql(INSERTAR_RECETA)
                .param(r.id()).param(r.numero()).param(r.tipo()).param(r.fechaEmision())
                .param(r.fechaVencimiento()).param(r.productoId()).param(r.clienteId())
                .param(r.datosPaciente()).param(r.datosProfesional()).param(r.dosis())
                .param(r.cantidadAutorizada()).param(r.archivoRespaldoUrl())
                .update();
    }

    @Override
    public Optional<String> estadoReceta(UUID recetaId) {
        return jdbc.sql("SELECT estado FROM recetas WHERE id = ?")
                .param(recetaId).query(String.class).optional();
    }

    @Override
    public void cambiarEstadoReceta(UUID recetaId, String estado) {
        jdbc.sql("UPDATE recetas SET estado = ? WHERE id = ?")
                .param(estado).param(recetaId).update();
    }

    private boolean existe(String sql, Object parametro) {
        return jdbc.sql(sql).param(parametro).query(Integer.class).optional().isPresent();
    }
}

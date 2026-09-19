package com.posfarmacia.identidad.adapters.persistence;

import com.posfarmacia.identidad.usecases.port.out.AdministracionPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Altas de usuarios, locales y cajas. */
@Repository
public class AdministracionJdbcAdapter implements AdministracionPort {

    private final JdbcClient jdbc;

    public AdministracionJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existeNombreUsuario(String nombreUsuario) {
        return jdbc.sql("SELECT 1 FROM usuarios WHERE nombre_usuario = ?")
                .param(nombreUsuario).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean existeLocal(UUID localId) {
        return jdbc.sql("SELECT 1 FROM locales WHERE id = ?")
                .param(localId).query(Integer.class).optional().isPresent();
    }

    @Override
    public List<UUID> idsDeRoles(List<String> nombres) {
        if (nombres == null || nombres.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT id FROM roles WHERE nombre = ANY (?)")
                .param(nombres.toArray(String[]::new))
                .query((rs, fila) -> rs.getObject("id", UUID.class))
                .list();
    }

    @Override
    public void insertarUsuario(UUID id, String nombreUsuario, String passwordHash, UUID localId) {
        jdbc.sql("""
                        INSERT INTO usuarios (id, nombre_usuario, password_hash, estado, local_id)
                        VALUES (?, ?, ?, 'ACTIVO', ?)
                        """)
                .param(id).param(nombreUsuario).param(passwordHash).param(localId)
                .update();
    }

    @Override
    public void asignarRoles(UUID usuarioId, List<UUID> rolesIds) {
        for (UUID rolId : rolesIds) {
            jdbc.sql("""
                            INSERT INTO usuarios_roles (usuario_id, rol_id) VALUES (?, ?)
                            ON CONFLICT DO NOTHING
                            """)
                    .param(usuarioId).param(rolId)
                    .update();
        }
    }

    @Override
    public void insertarLocal(UUID id, String nombre, String direccion) {
        jdbc.sql("INSERT INTO locales (id, nombre, direccion, activo) VALUES (?, ?, ?, true)")
                .param(id).param(nombre).param(direccion).update();
    }

    @Override
    public void insertarCaja(UUID id, String nombre, UUID localId) {
        jdbc.sql("INSERT INTO cajas (id, nombre, local_id, activa) VALUES (?, ?, ?, true)")
                .param(id).param(nombre).param(localId).update();
    }
}

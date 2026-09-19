package com.posfarmacia.identidad.adapters.persistence;

import com.posfarmacia.identidad.usecases.port.out.UsuarioPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioJdbcAdapter implements UsuarioPort {

    private static final String BASE = """
            SELECT id, nombre_usuario, password_hash, estado, local_id, email,
                   mfa_secret, mfa_habilitado
              FROM usuarios
            """;

    private static final String ROLES = """
            SELECT r.nombre FROM usuarios_roles ur
              JOIN roles r ON r.id = ur.rol_id
             WHERE ur.usuario_id = ?
            """;

    private final JdbcClient jdbc;

    public UsuarioJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Cuenta> porNombreUsuario(String nombreUsuario) {
        return jdbc.sql(BASE + " WHERE nombre_usuario = ?").param(nombreUsuario)
                .query(this::armar).optional();
    }

    @Override
    public Optional<Cuenta> porEmail(String email) {
        return jdbc.sql(BASE + " WHERE email = ?").param(email).query(this::armar).optional();
    }

    @Override
    public Optional<Cuenta> porId(UUID id) {
        return jdbc.sql(BASE + " WHERE id = ?").param(id).query(this::armar).optional();
    }

    private Cuenta armar(java.sql.ResultSet rs, int fila) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new Cuenta(id,
                rs.getString("nombre_usuario"),
                rs.getString("password_hash"),
                rs.getString("estado"),
                rs.getObject("local_id", UUID.class),
                rs.getString("email"),
                rs.getString("mfa_secret"),
                rs.getBoolean("mfa_habilitado"),
                rolesDe(id));
    }

    private List<String> rolesDe(UUID usuarioId) {
        return jdbc.sql(ROLES).param(usuarioId).query(String.class).list();
    }

    @Override
    public void guardarSecretoMfa(UUID usuarioId, String secreto, boolean habilitado) {
        jdbc.sql("UPDATE usuarios SET mfa_secret = ?, mfa_habilitado = ? WHERE id = ?")
                .param(secreto).param(habilitado).param(usuarioId)
                .update();
    }
}

package com.posfarmacia.identidad.usecases.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioPort {

    record Cuenta(UUID id, String nombreUsuario, String passwordHash, String estado,
                  UUID localId, String email, String mfaSecret, boolean mfaHabilitado,
                  List<String> roles) {
    }

    Optional<Cuenta> porNombreUsuario(String nombreUsuario);

    Optional<Cuenta> porEmail(String email);

    Optional<Cuenta> porId(UUID id);

    void guardarSecretoMfa(UUID usuarioId, String secreto, boolean habilitado);
}

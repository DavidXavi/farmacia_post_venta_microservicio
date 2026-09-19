package com.posfarmacia.identidad.usecases.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Altas de la administracion: usuarios, locales y cajas.
 *
 * <p>Aparte de {@link UsuarioPort}, que sirve al login y se ejecuta en cada inicio de
 * sesion. Esto lo usa un administrador cuando entra gente nueva o abre una botica.
 */
public interface AdministracionPort {

    boolean existeNombreUsuario(String nombreUsuario);

    boolean existeLocal(UUID localId);

    /** Ids de los roles cuyo nombre esta en la lista. Lo que no exista no vuelve. */
    List<UUID> idsDeRoles(List<String> nombres);

    void insertarUsuario(UUID id, String nombreUsuario, String passwordHash, UUID localId);

    void asignarRoles(UUID usuarioId, List<UUID> rolesIds);

    void insertarLocal(UUID id, String nombre, String direccion);

    void insertarCaja(UUID id, String nombre, UUID localId);
}

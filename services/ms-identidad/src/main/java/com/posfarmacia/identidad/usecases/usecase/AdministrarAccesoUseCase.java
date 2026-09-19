package com.posfarmacia.identidad.usecases.usecase;

import com.posfarmacia.contracts.Topicos;
import com.posfarmacia.contracts.eventos.OperacionAuditada;
import com.posfarmacia.identidad.usecases.port.out.AdministracionPort;
import com.posfarmacia.plataforma.outbox.OutboxRegistrador;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de usuarios y de locales con su caja.
 *
 * <p>Crear un usuario se audita siempre. Es la operacion que le da a alguien acceso a
 * cobrar, y en una revision de seguridad la primera pregunta es quien creo la cuenta
 * que hizo tal cosa.
 *
 * <p>Un usuario sin roles no puede hacer nada util y ademas es dificil de diagnosticar:
 * entra, ve el menu y todo le responde 403. Se rechaza en el alta.
 */
@Service
public class AdministrarAccesoUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdministrarAccesoUseCase.class);

    /** Minimo razonable. No es politica de seguridad completa, es el piso. */
    private static final int LARGO_MINIMO_CLAVE = 8;

    private final AdministracionPort administracion;
    private final PasswordEncoder encoder;
    private final OutboxRegistrador outbox;
    private final Clock reloj;

    public AdministrarAccesoUseCase(AdministracionPort administracion, PasswordEncoder encoder,
            OutboxRegistrador outbox, Clock reloj) {
        this.administracion = administracion;
        this.encoder = encoder;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public UUID registrarUsuario(String nombreUsuario, String password, UUID localId,
            List<String> roles, UUID creadorId) {
        String nombre = exigir(nombreUsuario, "nombre de usuario");
        if (password == null || password.length() < LARGO_MINIMO_CLAVE) {
            throw new IllegalArgumentException(
                    "La contrasena tiene que tener al menos " + LARGO_MINIMO_CLAVE + " caracteres");
        }
        if (administracion.existeNombreUsuario(nombre)) {
            throw new IllegalStateException("Ya existe el usuario " + nombre);
        }
        if (!administracion.existeLocal(localId)) {
            throw new IllegalArgumentException("No existe el local " + localId);
        }

        List<UUID> rolesIds = administracion.idsDeRoles(roles);
        if (rolesIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "El usuario necesita al menos un rol valido: sin roles entra pero no "
                            + "puede hacer nada, y el error es dificil de diagnosticar");
        }

        UUID id = UUID.randomUUID();
        administracion.insertarUsuario(id, nombre, encoder.encode(password), localId);
        administracion.asignarRoles(id, rolesIds);

        outbox.registrar("usuario", id, Topicos.AUDITORIA, id,
                new OperacionAuditada(creadorId, "ms-identidad", "ALTA_USUARIO", "usuario",
                        id.toString(), "Usuario " + nombre + " con roles " + roles,
                        null, String.join(",", roles), null, Instant.now(reloj)));

        log.warn("Usuario {} creado por {} con roles {}", nombre, creadorId, roles);
        return id;
    }

    /**
     * Alta de local.
     *
     * <p>Nace con una caja. Un local sin caja no puede vender, y la pantalla de
     * catalogos no tiene forma de crear una: dejarlo sin caja seria entregar un local
     * que no sirve y que nadie puede arreglar desde la interfaz.
     */
    @Transactional
    public UUID registrarLocal(String nombre, String direccion) {
        UUID id = UUID.randomUUID();
        administracion.insertarLocal(id, exigir(nombre, "nombre del local"), direccion);
        administracion.insertarCaja(UUID.randomUUID(), "Caja 1", id);
        log.info("Local {} creado con su Caja 1", nombre);
        return id;
    }

    private String exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + campo);
        }
        return valor.trim();
    }
}

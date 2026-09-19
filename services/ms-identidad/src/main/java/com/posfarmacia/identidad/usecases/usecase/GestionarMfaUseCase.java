package com.posfarmacia.identidad.usecases.usecase;

import com.posfarmacia.identidad.domain.Totp;
import com.posfarmacia.identidad.usecases.port.out.UsuarioPort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Alta y baja del segundo factor.
 *
 * <p>El alta va en dos pasos a proposito. Si el secreto quedara habilitado apenas se
 * genera, un usuario que cierra la pestana antes de escanear el QR se queda fuera de su
 * propia cuenta: el sistema le pide un codigo que nadie puede calcular. Por eso el
 * secreto se guarda deshabilitado, y solo se habilita cuando el usuario demuestra que
 * su telefono ya lo tiene, escribiendo un codigo valido.
 *
 * <p>El secreto viaja una sola vez, en la respuesta del primer paso. No hay endpoint
 * para volver a leerlo: quien lo pierde vuelve a empezar el alta.
 */
@Service
public class GestionarMfaUseCase {

    /** Lo que el usuario ve en Google Authenticator junto al codigo. */
    private final String emisor;

    private final UsuarioPort usuarios;

    public GestionarMfaUseCase(UsuarioPort usuarios,
            @Value("${pos.mfa.emisor:POS Farmacia}") String emisor) {
        this.usuarios = usuarios;
        this.emisor = emisor;
    }

    public record Registro(String secreto, String uriOtpauth) {
    }

    public boolean habilitado(UUID usuarioId) {
        return cuenta(usuarioId).mfaHabilitado();
    }

    /** Paso 1: genera el secreto y lo guarda apagado. */
    public Registro iniciarRegistro(UUID usuarioId) {
        var cuenta = cuenta(usuarioId);
        if (cuenta.mfaHabilitado()) {
            throw new IllegalStateException("La verificacion en dos pasos ya esta activada");
        }
        String secreto = Totp.generarSecreto();
        usuarios.guardarSecretoMfa(usuarioId, secreto, false);
        return new Registro(secreto, uriOtpauth(cuenta.nombreUsuario(), secreto));
    }

    /** Paso 2: el codigo prueba que el telefono ya tiene el secreto. Recien ahi se activa. */
    public void confirmarRegistro(UUID usuarioId, String codigo) {
        var cuenta = cuenta(usuarioId);
        if (cuenta.mfaSecret() == null) {
            throw new IllegalStateException("No hay un registro en curso. Vuelve a empezar.");
        }
        if (!Totp.verificar(cuenta.mfaSecret(), codigo, Instant.now())) {
            throw new IllegalArgumentException("El codigo no es valido. Revisa la hora del telefono.");
        }
        usuarios.guardarSecretoMfa(usuarioId, cuenta.mfaSecret(), true);
    }

    /** Baja: se borra el secreto, no solo se apaga la bandera. */
    public void deshabilitar(UUID usuarioId) {
        usuarios.guardarSecretoMfa(usuarioId, null, false);
    }

    private UsuarioPort.Cuenta cuenta(UUID usuarioId) {
        return usuarios.porId(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario " + usuarioId));
    }

    private String uriOtpauth(String nombreUsuario, String secreto) {
        String etiqueta = codificar(emisor + ":" + nombreUsuario);
        return "otpauth://totp/" + etiqueta
                + "?secret=" + secreto
                + "&issuer=" + codificar(emisor)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}

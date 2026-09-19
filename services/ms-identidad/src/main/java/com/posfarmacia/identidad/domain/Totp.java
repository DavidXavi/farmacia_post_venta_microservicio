package com.posfarmacia.identidad.domain;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Segundo factor TOTP (RFC 6238), compatible con Google Authenticator.
 *
 * <p>ponytail: sin libreria de TOTP. El algoritmo son dos llamadas de la JDK
 * (HmacSHA1 y un truncado) mas Base32, que es lo unico que la JDK no trae. Agregar una
 * dependencia para treinta lineas de codigo estandar es traerse un calendario de
 * actualizaciones de seguridad ajeno para no escribir treinta lineas.
 *
 * <p>Se conserva tal cual del proyecto original: la logica de autenticacion no cambio
 * al pasar a microservicios, solo cambio de casa.
 */
public final class Totp {

    private static final String ALFABETO_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int DIGITOS = 6;
    private static final long PASO_SEGUNDOS = 30;
    /** Se acepta un paso antes y uno despues: los relojes de los telefonos derivan. */
    private static final int VENTANA = 1;

    private Totp() {
    }

    public static String generarSecreto() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        return aBase32(bytes);
    }

    public static boolean verificar(String secretoBase32, String codigo, Instant ahora) {
        if (secretoBase32 == null || codigo == null) {
            return false;
        }
        long contador = ahora.getEpochSecond() / PASO_SEGUNDOS;
        for (int desfase = -VENTANA; desfase <= VENTANA; desfase++) {
            if (codigoPara(secretoBase32, contador + desfase).equals(codigo.trim())) {
                return true;
            }
        }
        return false;
    }

    static String codigoPara(String secretoBase32, long contador) {
        try {
            byte[] clave = deBase32(secretoBase32);
            byte[] datos = ByteBuffer.allocate(8).putLong(contador).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(clave, "HmacSHA1"));
            byte[] hash = mac.doFinal(datos);

            int desplazamiento = hash[hash.length - 1] & 0x0F;
            int binario = ((hash[desplazamiento] & 0x7F) << 24)
                    | ((hash[desplazamiento + 1] & 0xFF) << 16)
                    | ((hash[desplazamiento + 2] & 0xFF) << 8)
                    | (hash[desplazamiento + 3] & 0xFF);

            return String.format("%0" + DIGITOS + "d", binario % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el codigo TOTP", e);
        }
    }

    static String aBase32(byte[] datos) {
        StringBuilder salida = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                salida.append(ALFABETO_BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            salida.append(ALFABETO_BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return salida.toString();
    }

    static byte[] deBase32(String texto) {
        String limpio = texto.trim().replace("=", "").toUpperCase();
        int buffer = 0;
        int bits = 0;
        var salida = new java.io.ByteArrayOutputStream();
        for (char c : limpio.toCharArray()) {
            int valor = ALFABETO_BASE32.indexOf(c);
            if (valor < 0) {
                continue;
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return salida.toByteArray();
    }
}

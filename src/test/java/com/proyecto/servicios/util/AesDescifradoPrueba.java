package com.proyecto.servicios.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Solo para pruebas: descifra el payload para comprobar lo que se envía a GestoPago. */
public final class AesDescifradoPrueba {

    private AesDescifradoPrueba() {
    }

    public static String decrypt(String input, String ivBase64, String keyBase64) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // nosemgrep
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(keyBase64), "AES"),
                new IvParameterSpec(Base64.getDecoder().decode(ivBase64)));
        return new String(cipher.doFinal(Base64.getDecoder().decode(input)), StandardCharsets.UTF_8);
    }
}

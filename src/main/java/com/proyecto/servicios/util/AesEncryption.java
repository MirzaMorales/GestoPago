package com.proyecto.servicios.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Cifrado AES/CBC/PKCS5Padding con llave e IV en Base64, según el método de la
 * documentación de GestoPago (AesEncryption.AESCipher). Nunca imprime llave ni IV.
 * Solo cifra: el descifrado existe únicamente en las pruebas (para verificar el payload).
 */
public final class AesEncryption {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    private AesEncryption() {
    }

    public static String encrypt(String input, String ivBase64, String keyBase64) throws GeneralSecurityException {
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, ivBase64, keyBase64);
        return Base64.getEncoder().encodeToString(cipher.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }

    private static Cipher cipher(int mode, String ivBase64, String keyBase64) throws GeneralSecurityException {
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] key = Base64.getDecoder().decode(keyBase64);
        // CBC es obligatorio: GestoPago solo acepta el payload cifrado con AES-256-CBC (no admite GCM).
        // No hay "padding oracle": la aplicación solo CIFRA lo que envía; nunca descifra datos recibidos.
        // nosemgrep: java.lang.security.audit.cbc-padding-oracle.cbc-padding-oracle
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher;
    }
}

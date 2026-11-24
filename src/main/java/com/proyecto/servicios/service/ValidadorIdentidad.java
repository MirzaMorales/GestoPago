package com.proyecto.servicios.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Coherencia entre los datos de identidad: la CURP y el RFC llevan la fecha de nacimiento (AAMMDD) y la CURP
 * el sexo (H o M). Detecta CURP/RFC de otra persona o mal capturados aunque tengan un formato válido.
 */
public final class ValidadorIdentidad {

    private static final DateTimeFormatter AAMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    private ValidadorIdentidad() {
    }

    /** Mensaje de error, o null si todo coincide. */
    public static String errorCurp(String curp, LocalDate fechaNacimiento, String sexo) {
        if (curp == null || curp.length() != 18 || fechaNacimiento == null) {
            return null; // el formato ya lo valida el DTO
        }
        String c = curp.toUpperCase(Locale.ROOT);
        if (!c.substring(4, 10).equals(fechaNacimiento.format(AAMMDD))) {
            return "La CURP no coincide con la fecha de nacimiento";
        }
        Character esperado = letraSexo(sexo);
        if (esperado != null && c.charAt(10) != esperado) {
            return "La CURP no coincide con el sexo indicado";
        }
        return null;
    }

    /** RFC de persona física (13) o moral (12): la fecha va después de las 4 o 3 letras iniciales. */
    public static String errorRfc(String rfc, LocalDate fechaNacimiento) {
        if (rfc == null || fechaNacimiento == null || (rfc.length() != 12 && rfc.length() != 13)) {
            return null;
        }
        int inicio = rfc.length() == 13 ? 4 : 3;
        if (!rfc.substring(inicio, inicio + 6).equals(fechaNacimiento.format(AAMMDD))) {
            return "El RFC no coincide con la fecha de nacimiento";
        }
        return null;
    }

    /** H para hombre, M para mujer; null si el valor no permite saberlo. */
    static Character letraSexo(String sexo) {
        if (sexo == null) {
            return null;
        }
        String s = sexo.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("MASC") || s.equals("H") || s.equals("HOMBRE")) {
            return 'H';
        }
        if (s.startsWith("FEM") || s.equals("F") || s.equals("MUJER")) {
            return 'M';
        }
        return null;
    }
}

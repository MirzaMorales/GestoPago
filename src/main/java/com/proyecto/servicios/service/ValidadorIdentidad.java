package com.proyecto.servicios.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/**
 * Coherencia entre los datos de identidad: la CURP y el RFC llevan la fecha de nacimiento (AAMMDD) y la CURP
 * el sexo (H o M). Detecta CURP/RFC de otra persona o mal capturados aunque tengan un formato válido.
 */
public final class ValidadorIdentidad {

    private static final DateTimeFormatter AAMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    /** Claves de entidad federativa que admite la CURP (posiciones 12 y 13); NE es nacido en el extranjero. */
    private static final Set<String> ENTIDADES = Set.of("AS", "BC", "BS", "CC", "CL", "CM", "CS", "CH", "DF", "DG",
            "GT", "GR", "HG", "JC", "MC", "MN", "MS", "NT", "NL", "OC", "PL", "QT", "QR", "SP", "SL", "SR", "TC",
            "TS", "TL", "VZ", "YN", "ZS", "NE");

    private ValidadorIdentidad() {
    }

    /** Mensaje de error, o null si todo coincide. */
    public static String errorCurp(String curp, LocalDate fechaNacimiento, String sexo) {
        if (curp == null || curp.length() != 18 || fechaNacimiento == null) {
            return null; // el formato ya lo valida el DTO
        }
        String c = curp.toUpperCase(Locale.ROOT);
        if (!ENTIDADES.contains(c.substring(11, 13))) {
            return "La CURP tiene una clave de entidad no válida";
        }
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

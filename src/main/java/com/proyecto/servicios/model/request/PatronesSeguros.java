package com.proyecto.servicios.model.request;

/**
 * Listas blancas de caracteres para los campos de texto que llegan del cliente.
 * Se acepta solo lo que un dato legítimo necesita; así se rechaza HTML, scripts y caracteres de control
 * (por ejemplo {@code <script>}, {@code onerror=}, {@code {"$ne":1}}, {@code \u0000}) antes de llegar al negocio.
 */
public final class PatronesSeguros {

    /** Texto libre: letras (con acentos), números, espacios y . , ' ’ - # / & ( ) ° º ª */
    public static final String TEXTO_LIBRE = "^[\\p{L}\\p{M}\\p{N} .,'’\\-#/&()°ºª]*$";
    public static final String MENSAJE_TEXTO_LIBRE =
            "Solo se admiten letras, números, espacios y los signos . , ' - # / & ( )";

    /**
     * Colonia, municipio y estado: como texto libre más [ ] " y ´, que aparecen en nombres
     * oficiales de SEPOMEX (p. ej. {@code San Rafael I [Potrero]}). Además se validan contra el catálogo.
     */
    public static final String UBICACION = "^[\\p{L}\\p{M}\\p{N} .,'’\\-#/&()°ºª\\[\\]\"´]*$";
    public static final String MENSAJE_UBICACION = "Contiene caracteres no permitidos";

    /** Catálogos de una palabra o frase corta (sexo, estado civil, nacionalidad). */
    public static final String SOLO_LETRAS = "^[\\p{L}\\p{M} ().\\-]*$";
    public static final String MENSAJE_SOLO_LETRAS = "Solo se admiten letras y espacios";

    /** Correo sin comillas ni comentarios (formas válidas en RFC 5322 que no usa ningún usuario real). */
    public static final String CORREO = "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$";
    public static final String MENSAJE_CORREO = "El formato del correo electrónico es inválido";

    /** Fragmento de correo para búsquedas parciales. */
    public static final String CORREO_PARCIAL = "^[A-Za-z0-9._%+@\\-]*$";

    /** Longitud máxima de contraseñas: BCrypt solo usa 72 bytes y un valor enorme sería un vector de abuso. */
    public static final int PASSWORD_MAX = 128;
    public static final String MENSAJE_PASSWORD_MAX = "La contraseña no puede exceder 128 caracteres";

    private PatronesSeguros() {
    }
}

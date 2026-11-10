package com.proyecto.servicios.exception;

/** El usuario ya tiene una sesión abierta en otra plataforma y no pidió cerrarla. */
public class SesionActivaException extends RuntimeException {

    private final String plataformaActiva;

    public SesionActivaException(String plataformaActiva) {
        super("Ya existe una sesión activa en la plataforma " + plataformaActiva
                + ". Cierre la sesión anterior para continuar");
        this.plataformaActiva = plataformaActiva;
    }

    public String getPlataformaActiva() {
        return plataformaActiva;
    }
}

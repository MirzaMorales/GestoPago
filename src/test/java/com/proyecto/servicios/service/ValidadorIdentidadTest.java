package com.proyecto.servicios.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ValidadorIdentidadTest {

    private static final LocalDate NACIMIENTO = LocalDate.of(1995, 5, 20);

    @Test
    void curpYRfcCoherentesSonValidos() {
        assertNull(ValidadorIdentidad.errorCurp("LORA950520MDFPZN01", NACIMIENTO, "Femenino"));
        assertNull(ValidadorIdentidad.errorCurp("PEGJ950520HDFRMN01", NACIMIENTO, "MASCULINO"));
        assertNull(ValidadorIdentidad.errorRfc("LORA950520AB1", NACIMIENTO));
        assertNull(ValidadorIdentidad.errorRfc("ABC950520AB1", NACIMIENTO)); // persona moral (12)
    }

    @Test
    void curpConOtraFechaOtroSexoSeRechaza() {
        assertEquals("La CURP no coincide con la fecha de nacimiento",
                ValidadorIdentidad.errorCurp("LORA960520MDFPZN01", NACIMIENTO, "Femenino"));
        assertEquals("La CURP no coincide con el sexo indicado",
                ValidadorIdentidad.errorCurp("LORA950520HDFPZN01", NACIMIENTO, "Femenino"));
    }

    @Test
    void rfcConOtraFechaSeRechaza() {
        assertEquals("El RFC no coincide con la fecha de nacimiento",
                ValidadorIdentidad.errorRfc("LORA950521AB1", NACIMIENTO));
    }

    @Test
    void sexoDesconocidoNoBloqueaLaRevisionDeFecha() {
        assertNull(ValidadorIdentidad.errorCurp("LORA950520XDFPZN01", NACIMIENTO, "No especificado"));
    }
}

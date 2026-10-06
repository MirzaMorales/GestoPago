package com.proyecto.servicios.model.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistroClienteValidationTest {

    @Test
    void acceptsLowercaseIdentityCodesAndNamesWithCommonPunctuation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            RegistroClienteRequest request = validRequest();
            request.setNombre("María-José");
            request.setCurp("pegj950520hdfrmn01");
            request.setRfc("pegj950520xxx");

            assertTrue(validator.validate(request).isEmpty());
        }
    }

    @Test
    void rejectsIncomeBeyondDatabasePrecisionOrScale() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            RegistroClienteRequest request = validRequest();
            request.setIngresoMensual(new BigDecimal("10000000000000.001"));

            assertTrue(validator.validate(request).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("ingresoMensual")));
        }
    }

    private RegistroClienteRequest validRequest() {
        return RegistroClienteRequest.builder()
                .nombre("María Jose")
                .apellidoPaterno("Pérez")
                .apellidoMaterno("Gómez")
                .fechaNacimiento(LocalDate.of(1995, 5, 20))
                .curp("PEGJ950520HDFRMN01")
                .rfc("PEGJ950520XXX")
                .sexo("FEMENINO")
                .estadoCivil("SOLTERA")
                .correo("maria@example.com")
                .telefonoMovil("5512345678")
                .ocupacion("Ingeniera")
                .empresa("Ejemplo")
                .ingresoMensual(new BigDecimal("25000.00"))
                .domicilio(DomicilioRequest.builder()
                        .calle("Av. Reforma")
                        .numeroExterior("123")
                        .colonia("Centro")
                        .municipio("Cuauhtémoc")
                        .estado("CDMX")
                        .codigoPostal("06000")
                        .pais("México")
                        .build())
                .password("SecureP@ss123")
                .build();
    }
}

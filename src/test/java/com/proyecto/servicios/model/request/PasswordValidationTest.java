package com.proyecto.servicios.model.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PasswordValidationTest {

    @Test
    void acceptsAnyNonAlphanumericSpecialCharacter() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            CambiaPasswordRequest request = CambiaPasswordRequest.builder()
                    .passwordActual("CurrentPass1!")
                    .nuevaPassword("Strong+Pass1")
                    .build();

            assertTrue(validator.validate(request).isEmpty());
        }
    }

    @Test
    void rejectsPasswordWithoutSpecialCharacter() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            CambiaPasswordRequest request = CambiaPasswordRequest.builder()
                    .passwordActual("CurrentPass1!")
                    .nuevaPassword("StrongPass1")
                    .build();

            assertFalse(validator.validate(request).isEmpty());
        }
    }
}

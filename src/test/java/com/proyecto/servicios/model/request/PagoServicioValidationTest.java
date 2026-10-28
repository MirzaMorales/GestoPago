package com.proyecto.servicios.model.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagoServicioValidationTest {

    private Set<String> camposInvalidos(PagoServicioRequest request) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(request).stream()
                    .map(v -> v.getPropertyPath().toString())
                    .collect(Collectors.toSet());
        }
    }

    private PagoServicioRequest valido() {
        return PagoServicioRequest.builder()
                .numeroCuenta("1234567890123456")
                .pin("1234")
                .monto(new BigDecimal("100.50"))
                .telefonoReferencia("5555555551")
                .idServicio(133)
                .idProducto(582)
                .tipoFront(1)
                .build();
    }

    @Test
    void requestValido_noTieneErrores() {
        assertTrue(camposInvalidos(valido()).isEmpty());
    }

    @Test
    void requestVacio_marcaTodosLosCamposObligatorios() {
        assertEquals(
                Set.of("numeroCuenta", "pin", "monto", "telefonoReferencia", "idServicio", "idProducto", "tipoFront"),
                camposInvalidos(new PagoServicioRequest()));
    }

    @Test
    void rechazaMontoCeroNegativoOConMasDeDosDecimales() {
        for (String monto : new String[]{"0", "-5", "10.123"}) {
            PagoServicioRequest request = valido();
            request.setMonto(new BigDecimal(monto));
            assertEquals(Set.of("monto"), camposInvalidos(request), "monto " + monto);
        }
    }

    @Test
    void rechazaPinYCuentaConFormatoInvalido() {
        PagoServicioRequest request = valido();
        request.setPin("12a4");
        request.setNumeroCuenta("123");

        assertEquals(Set.of("pin", "numeroCuenta"), camposInvalidos(request));
    }

    @Test
    void rechazaReferenciaConCaracteresEspeciales() {
        PagoServicioRequest request = valido();
        request.setTelefonoReferencia("55 55;DROP");

        assertEquals(Set.of("telefonoReferencia"), camposInvalidos(request));
    }
}

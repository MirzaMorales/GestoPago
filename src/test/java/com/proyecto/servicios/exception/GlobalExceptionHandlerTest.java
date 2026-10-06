package com.proyecto.servicios.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            new ApiErrorResponseWriter(new ObjectMapper().findAndRegisterModules())
    );

    @Test
    void returnsFieldDetailsAndBadRequestForInvalidInput() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "correo", "El correo es obligatorio"));

        var response = handler.handleValidationExceptions(
                new org.springframework.validation.BindException(bindingResult)
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("El correo es obligatorio", response.getBody().getDetalles().get("correo"));
    }

    @Test
    void doesNotExposeInternalExceptionMessagesInServerErrors() {
        var response = handler.handleGeneralException(new IllegalStateException("database password leaked"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getStatus());
        assertFalse(response.getBody().getMensaje().contains("database password leaked"));
    }

    @Test
    void writesStructuredJsonForSecurityErrors() throws Exception {
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(
                new ObjectMapper().findAndRegisterModules()
        );
        var servletResponse = new org.springframework.mock.web.MockHttpServletResponse();

        writer.write(servletResponse, HttpStatus.UNAUTHORIZED, "Se requiere autenticación");

        assertEquals(401, servletResponse.getStatus());
        assertEquals("application/json;charset=UTF-8", servletResponse.getContentType());
        assertNotNull(servletResponse.getContentAsString());
        var responseBody = new ObjectMapper().findAndRegisterModules()
                .readTree(servletResponse.getContentAsString());
        assertEquals(401, responseBody.get("status").asInt());
        assertEquals("Se requiere autenticación", responseBody.get("mensaje").asText());
    }
}

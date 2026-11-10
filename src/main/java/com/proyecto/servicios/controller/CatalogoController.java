package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.response.ColoniaResponse;
import com.proyecto.servicios.service.CatalogoDomicilioService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Catálogo de domicilios (SEPOMEX) para el formulario de registro. Es público y de solo lectura:
 * se usa antes de que exista una sesión.
 */
@Validated
@RestController
@RequestMapping("/catalogos")
public class CatalogoController {

    private static final CacheControl CACHE = CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic();

    private final CatalogoDomicilioService catalogo;

    public CatalogoController(CatalogoDomicilioService catalogo) {
        this.catalogo = catalogo;
    }

    /** Colonias de un código postal, con su municipio y estado. Lista vacía si el código no existe. */
    @GetMapping("/codigos-postales/{codigoPostal}")
    public ResponseEntity<GenericResponse<List<ColoniaResponse>>> porCodigoPostal(
            @PathVariable @Pattern(regexp = "^\\d{5}$", message = "El código postal debe contener 5 dígitos")
            String codigoPostal) {
        return ok("Colonias del código postal", catalogo.porCodigoPostal(codigoPostal));
    }

    @GetMapping("/estados")
    public ResponseEntity<GenericResponse<List<String>>> estados() {
        return ok("Estados", catalogo.estados());
    }

    @GetMapping("/municipios")
    public ResponseEntity<GenericResponse<List<String>>> municipios(
            @RequestParam @NotBlank(message = "El estado es obligatorio")
            @Size(max = 100, message = "El estado no puede exceder 100 caracteres") String estado) {
        return ok("Municipios del estado", catalogo.municipios(estado));
    }

    @GetMapping("/colonias")
    public ResponseEntity<GenericResponse<List<ColoniaResponse>>> colonias(
            @RequestParam @NotBlank(message = "El estado es obligatorio")
            @Size(max = 100, message = "El estado no puede exceder 100 caracteres") String estado,
            @RequestParam @NotBlank(message = "El municipio es obligatorio")
            @Size(max = 100, message = "El municipio no puede exceder 100 caracteres") String municipio) {
        return ok("Colonias del municipio", catalogo.colonias(estado, municipio));
    }

    private static <T> ResponseEntity<GenericResponse<T>> ok(String mensaje, T datos) {
        return ResponseEntity.ok().cacheControl(CACHE).body(GenericResponse.ok(HttpStatus.OK, mensaje, datos));
    }
}

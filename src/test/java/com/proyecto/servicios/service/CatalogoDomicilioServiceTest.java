package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.catalogo.CodigoPostal;
import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.model.request.DomicilioRequest;
import com.proyecto.servicios.model.response.ColoniaResponse;
import com.proyecto.servicios.repositorys.catalogo.CodigoPostalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogoDomicilioServiceTest {

    private CodigoPostalRepository repository;
    private CatalogoDomicilioService service;

    private final DomicilioRequest domicilio = DomicilioRequest.builder()
            .calle("Av. Álvaro Obregón").numeroExterior("10")
            .colonia(" Roma Norte ").municipio("Cuauhtémoc").estado("Ciudad de México")
            .codigoPostal("06700").pais("México")
            .build();

    @BeforeEach
    void setUp() {
        repository = mock(CodigoPostalRepository.class);
        service = new CatalogoDomicilioService(repository);
    }

    @Test
    void aceptaDomicilioQueExisteEnElCatalogo() {
        when(repository.count()).thenReturn(158_000L);
        when(repository.existsByCodigoPostalAndAsentamientoIgnoreCaseAndMunicipioIgnoreCaseAndEstadoIgnoreCase(
                "06700", "Roma Norte", "Cuauhtémoc", "Ciudad de México")).thenReturn(true);

        assertDoesNotThrow(() -> service.validar(domicilio));
    }

    @Test
    void rechazaColoniaQueNoCorrespondeAlCodigoPostal() {
        when(repository.count()).thenReturn(158_000L);
        when(repository.existsByCodigoPostalAndAsentamientoIgnoreCaseAndMunicipioIgnoreCaseAndEstadoIgnoreCase(
                anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        ValidacionNegocioException ex = assertThrows(ValidacionNegocioException.class, () -> service.validar(domicilio));
        assertEquals(CatalogoDomicilioService.MENSAJE_DOMICILIO_INVALIDO, ex.getMessage());
    }

    @Test
    void sinCatalogoCargadoNoBloqueaElRegistro() {
        when(repository.count()).thenReturn(0L);

        assertDoesNotThrow(() -> service.validar(domicilio));
        verify(repository, never()).existsByCodigoPostalAndAsentamientoIgnoreCaseAndMunicipioIgnoreCaseAndEstadoIgnoreCase(
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void recuerdaQueElCatalogoEstaCargadoSinContarCadaVez() {
        when(repository.count()).thenReturn(10L);
        when(repository.existsByCodigoPostalAndAsentamientoIgnoreCaseAndMunicipioIgnoreCaseAndEstadoIgnoreCase(
                anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        service.validar(domicilio);
        service.validar(domicilio);

        verify(repository, times(1)).count();
    }

    @Test
    void porCodigoPostalDevuelveColoniaMunicipioYEstado() {
        when(repository.findByCodigoPostalOrderByAsentamientoAsc("06700")).thenReturn(List.of(
                CodigoPostal.builder().codigoPostal("06700").asentamiento("Roma Norte").tipoAsentamiento("Colonia")
                        .municipio("Cuauhtémoc").estado("Ciudad de México").ciudad("Ciudad de México").build()));

        List<ColoniaResponse> colonias = service.porCodigoPostal("06700");

        assertEquals(1, colonias.size());
        assertEquals("Roma Norte", colonias.get(0).colonia());
        assertEquals("Cuauhtémoc", colonias.get(0).municipio());
        assertEquals("Ciudad de México", colonias.get(0).estado());
    }
}

package com.proyecto.servicios.integration;

import com.proyecto.servicios.config.SecurityConfig;
import com.proyecto.servicios.controller.CatalogoController;
import com.proyecto.servicios.exception.ApiErrorResponseWriter;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.model.response.ColoniaResponse;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.security.JwtAuthenticationFilter;
import com.proyecto.servicios.security.JwtTokenProvider;
import com.proyecto.servicios.security.SesionService;
import com.proyecto.servicios.service.CatalogoDomicilioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Catálogo SEPOMEX por la cadena de seguridad real: público para lectura, con validación de parámetros. */
@WebMvcTest
@ContextConfiguration(classes = {
        CatalogoController.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        SesionService.class, ApiErrorResponseWriter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.jwt.secret=clave-de-pruebas-con-mas-de-32-caracteres-123456")
class CatalogoWebTest {

    @Autowired private MockMvc mvc;

    @MockBean private CatalogoDomicilioService catalogo;
    @MockBean private UsuarioRepository usuarioRepository;
    @MockBean private StringRedisTemplate redis;

    @Test
    void codigoPostalEsPublicoYDevuelveSusColonias() throws Exception {
        when(catalogo.porCodigoPostal("06700")).thenReturn(List.of(
                new ColoniaResponse("06700", "Roma Norte", "Colonia", "Cuauhtémoc", "Ciudad de México", "Ciudad de México")));

        mvc.perform(get("/catalogos/codigos-postales/06700"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                .andExpect(jsonPath("$.datos[0].colonia").value("Roma Norte"))
                .andExpect(jsonPath("$.datos[0].municipio").value("Cuauhtémoc"))
                .andExpect(jsonPath("$.datos[0].estado").value("Ciudad de México"));
    }

    @Test
    void codigoPostalConFormatoInvalidoResponde400SinConsultar() throws Exception {
        mvc.perform(get("/catalogos/codigos-postales/12ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Error de validación en la solicitud"))
                .andExpect(jsonPath("$.detalles.codigoPostal").value("El código postal debe contener 5 dígitos"));
        verify(catalogo, never()).porCodigoPostal(anyString());
    }

    @Test
    void estadosMunicipiosYColoniasSonPublicos() throws Exception {
        when(catalogo.estados()).thenReturn(List.of("Guanajuato", "Jalisco"));
        when(catalogo.municipios("Guanajuato")).thenReturn(List.of("San Miguel de Allende"));
        when(catalogo.colonias("Guanajuato", "San Miguel de Allende")).thenReturn(List.of(
                new ColoniaResponse("37700", "Centro", "Colonia", "San Miguel de Allende", "Guanajuato", "San Miguel de Allende")));

        mvc.perform(get("/catalogos/estados")).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[1]").value("Jalisco"));
        mvc.perform(get("/catalogos/municipios").param("estado", "Guanajuato")).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0]").value("San Miguel de Allende"));
        mvc.perform(get("/catalogos/colonias").param("estado", "Guanajuato").param("municipio", "San Miguel de Allende"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].codigoPostal").value("37700"));
    }

    @Test
    void municipiosSinEstadoResponde400() throws Exception {
        mvc.perform(get("/catalogos/municipios")).andExpect(status().isBadRequest());
    }

    @Test
    void soloLecturaEsPublica() throws Exception {
        mvc.perform(post("/catalogos/estados")).andExpect(status().isUnauthorized());
    }
}

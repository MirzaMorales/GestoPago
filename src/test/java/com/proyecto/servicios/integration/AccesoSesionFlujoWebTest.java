package com.proyecto.servicios.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.SecurityConfig;
import com.proyecto.servicios.controller.AuthController;
import com.proyecto.servicios.controller.PagoController;
import com.proyecto.servicios.entity.Cliente;
import com.proyecto.servicios.entity.Rol;
import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.exception.ApiErrorResponseWriter;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.model.response.CompraResponse;
import com.proyecto.servicios.model.response.PaginaResponse;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.security.AccesosService;
import com.proyecto.servicios.security.JwtAuthenticationFilter;
import com.proyecto.servicios.security.JwtTokenProvider;
import com.proyecto.servicios.security.LoginIntentosService;
import com.proyecto.servicios.security.Otp2faService;
import com.proyecto.servicios.security.SesionService;
import com.proyecto.servicios.service.HistorialService;
import com.proyecto.servicios.service.Impl.AuthServiceImpl;
import com.proyecto.servicios.service.PagoService;
import com.proyecto.servicios.service.RecuperacionPasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo de acceso de punta a punta por la cadena de seguridad REAL (filtro JWT, sesión única, roles, manejo de errores):
 * login → 2FA → recurso protegido → 409 por sesión activa → reemplazo de sesión → logout.
 * Solo se simulan Redis (mapa en memoria), el envío del correo y los servicios de negocio ajenos al acceso.
 */
@WebMvcTest
@ContextConfiguration(classes = {
        AuthController.class, PagoController.class, AuthServiceImpl.class, RecuperacionPasswordService.class, SecurityConfig.class,
        JwtAuthenticationFilter.class, JwtTokenProvider.class, SesionService.class, AccesosService.class,
        ApiErrorResponseWriter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.jwt.secret=clave-de-pruebas-con-mas-de-32-caracteres-123456")
class AccesoSesionFlujoWebTest {

    private static final String CLAVE = "Clave#123";

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper json;

    @MockBean private UsuarioRepository usuarioRepository;
    @MockBean private CuentaRepository cuentaRepository;
    @MockBean private StringRedisTemplate redis;
    @MockBean private Otp2faService otp2fa;
    @MockBean private LoginIntentosService loginIntentos;
    @MockBean private PagoService pagoService;
    @MockBean private HistorialService historialService;

    private final Map<String, String> store = new HashMap<>();
    private String plataformaDelDesafio;
    private boolean cerrarDelDesafio;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
        doAnswer(i -> store.put(i.getArgument(0), i.getArgument(1))).when(ops)
                .set(anyString(), anyString(), any(Duration.class));
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(i -> store.putIfAbsent(i.getArgument(0), i.getArgument(1)) == null);
        when(redis.delete(anyString())).thenAnswer(i -> store.remove(i.<String>getArgument(0)) != null);

        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setActivo(true);
        Usuario cli = Usuario.builder().id(1L).correo("cliente@example.com")
                .password(passwordEncoder.encode(CLAVE)).rol(Rol.CLIENTE).activo(true).cliente(cliente).build();
        Usuario admin = Usuario.builder().id(2L).correo("admin@example.com")
                .password(passwordEncoder.encode(CLAVE)).rol(Rol.ADMIN).activo(true).build();
        for (Usuario u : List.of(cli, admin)) {
            when(usuarioRepository.findByCorreo(u.getCorreo())).thenReturn(Optional.of(u));
            when(usuarioRepository.findActiveByCorreo(u.getCorreo())).thenReturn(Optional.of(u));
            when(usuarioRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }

        when(otp2fa.crear(anyLong(), anyString(), anyString(), anyBoolean())).thenAnswer(i -> {
            plataformaDelDesafio = i.getArgument(2);
            cerrarDelDesafio = i.getArgument(3);
            return new Otp2faService.Desafio("desafio-" + i.getArgument(0), "c***@example.com", 300);
        });
        when(otp2fa.crearRecuperacion(anyLong(), anyString())).thenAnswer(i -> {
            plataformaDelDesafio = Otp2faService.PROPOSITO_RECUPERACION;
            cerrarDelDesafio = false;
            return new Otp2faService.Desafio("desafio-" + i.getArgument(0), "c***@example.com", 300);
        });
        when(otp2fa.verificar(anyString(), eq("123456"))).thenAnswer(i -> new Otp2faService.Verificado(
                Long.valueOf(i.<String>getArgument(0).substring("desafio-".length())),
                plataformaDelDesafio, cerrarDelDesafio));

        when(historialService.listar(anyString(), anyInt(), anyInt()))
                .thenReturn(PaginaResponse.<CompraResponse>builder().contenido(List.of()).pagina(0).tamano(20)
                        .totalElementos(0).totalPaginas(0).build());
    }

    private ResultActions login(String correo, String plataforma, boolean cerrarAnterior) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(
                "{\"correo\":\"" + correo + "\",\"password\":\"" + CLAVE + "\",\"plataforma\":\"" + plataforma
                        + "\",\"cerrarSesionAnterior\":" + cerrarAnterior + "}"));
    }

    private String verificar(String desafioId) throws Exception {
        String body = mvc.perform(post("/auth/verificar-2fa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafioId\":\"" + desafioId + "\",\"codigo\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(body);
        assertNotNull(n.get("accessToken"));
        return n.get("accessToken").asText();
    }

    private ResultActions historial(String token) throws Exception {
        var req = post("/pagos/historial").contentType(MediaType.APPLICATION_JSON).content("{}");
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    @Test
    void elLoginNoEntregaTokenSoloPideElSegundoFactor() throws Exception {
        String body = login("cliente@example.com", "MOVIL", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiere2fa").value(true))
                .andExpect(jsonPath("$.desafioId").value("desafio-1"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("accessToken"), "el token solo se entrega tras validar el 2FA");
    }

    @Test
    void sinTokenLosRecursosProtegidosDan401() throws Exception {
        historial(null).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void flujoCompletoLoginDosFactoresYRecursoProtegido() throws Exception {
        login("cliente@example.com", "MOVIL", false).andExpect(status().isOk());
        String token = verificar("desafio-1");

        historial(token).andExpect(status().isOk()).andExpect(jsonPath("$.datos.totalElementos").value(0));
    }

    @Test
    void conUnaSesionActivaElSegundoLoginDa409IndicandoLaPlataforma() throws Exception {
        login("cliente@example.com", "MOVIL", false).andExpect(status().isOk());
        verificar("desafio-1");

        login("cliente@example.com", "WEB", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detalles.error").value("SESION_ACTIVA"))
                .andExpect(jsonPath("$.detalles.plataformaActiva").value("MOVIL"));
    }

    @Test
    void cerrarLaSesionAnteriorInvalidaElTokenViejoYElNuevoFunciona() throws Exception {
        login("cliente@example.com", "MOVIL", false).andExpect(status().isOk());
        String tokenMovil = verificar("desafio-1");

        login("cliente@example.com", "WEB", true).andExpect(status().isOk());
        String tokenWeb = verificar("desafio-1");

        historial(tokenMovil).andExpect(status().isUnauthorized());
        historial(tokenWeb).andExpect(status().isOk());
    }

    @Test
    void elLogoutCierraLaSesionEnElServidor() throws Exception {
        login("cliente@example.com", "WEB", false).andExpect(status().isOk());
        String token = verificar("desafio-1");

        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        historial(token).andExpect(status().isUnauthorized());
        // y ya se puede abrir una sesión nueva sin conflicto
        login("cliente@example.com", "MOVIL", false).andExpect(status().isOk());
    }

    @Test
    void unTokenAlteradoOInventadoSeRechaza() throws Exception {
        login("cliente@example.com", "WEB", false).andExpect(status().isOk());
        String token = verificar("desafio-1");

        // se altera el contenido (payload) conservando la firma original: debe fallar la verificación
        String[] partes = token.split("[.]");
        char original = partes[1].charAt(12);
        String payloadAlterado = partes[1].substring(0, 12) + (original == 'A' ? 'B' : 'A') + partes[1].substring(13);
        historial(partes[0] + "." + payloadAlterado + "." + partes[2]).andExpect(status().isUnauthorized());
        historial("no.es.un.jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void unAdminNoPuedeUsarElHistorialDeClientes() throws Exception {
        login("admin@example.com", "WEB", false).andExpect(status().isOk());
        String token = verificar("desafio-2");

        historial(token).andExpect(status().isForbidden());
    }

    @Test
    void camposNoPermitidosEnElJsonSeRechazan() throws Exception {
        // intento de escalar privilegios agregando un campo que la API no acepta
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"correo\":\"cliente@example.com\",\"password\":\"" + CLAVE + "\",\"rol\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La solicitud contiene campos no permitidos"))
                .andExpect(jsonPath("$.detalles.rol").value("Campo no permitido"));
        verifyNoInteractions(otp2fa);
    }

    @Test
    void lasRespuestasLlevanCabecerasDeSeguridad() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    void credencialesInvalidasDan401SinPedirCodigo() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"correo\":\"cliente@example.com\",\"password\":\"Incorrecta#1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(containsString("Credenciales")));
        verifyNoInteractions(otp2fa);
    }

    @Test
    void elLoginValidaElFormatoDeLaSolicitud() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"no-es-correo\",\"password\":\"x\",\"plataforma\":\"TV\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ olvidé mi contraseña

    private ResultActions solicitarRecuperacion(String correo) throws Exception {
        return mvc.perform(post("/auth/recuperar/solicitar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + correo + "\"}"));
    }

    private ResultActions restablecer(String desafioId, String nueva) throws Exception {
        return mvc.perform(post("/auth/recuperar/restablecer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"desafioId\":\"" + desafioId + "\",\"codigo\":\"123456\",\"nuevaPassword\":\"" + nueva + "\"}"));
    }

    @Test
    void recuperacionConCorreoNoRegistradoRespondeIgualSinEnviarCodigo() throws Exception {
        solicitarRecuperacion("nadie@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desafioId").exists())
                .andExpect(jsonPath("$.correoEnmascarado").value("n***@example.com"));
        verify(otp2fa, never()).crearRecuperacion(anyLong(), anyString());
    }

    @Test
    void restablecerCambiaLaContrasenaYCierraLaSesionAbierta() throws Exception {
        login("cliente@example.com", "MOVIL", false).andExpect(status().isOk());
        String tokenViejo = verificar("desafio-1");

        solicitarRecuperacion("cliente@example.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.desafioId").value("desafio-1"));
        restablecer("desafio-1", "Nueva#Clave9").andExpect(status().isOk());

        historial(tokenViejo).andExpect(status().isUnauthorized());           // la sesión anterior se cerró
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"cliente@example.com\",\"password\":\"Nueva#Clave9\"}"))
                .andExpect(status().isOk());                                  // entra con la nueva
    }

    @Test
    void unCodigoDeRecuperacionNoAbreSesion() throws Exception {
        solicitarRecuperacion("cliente@example.com").andExpect(status().isOk());
        mvc.perform(post("/auth/verificar-2fa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafioId\":\"desafio-1\",\"codigo\":\"123456\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void unCodigoDeLoginNoRestableceContrasenas() throws Exception {
        login("cliente@example.com", "WEB", false).andExpect(status().isOk()); // desafío de login
        restablecer("desafio-1", "Nueva#Clave9").andExpect(status().isUnauthorized());
    }

    @Test
    void restablecerExigeContrasenaSegura() throws Exception {
        restablecer("desafio-1", "debil").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.nuevaPassword").exists());
    }
}

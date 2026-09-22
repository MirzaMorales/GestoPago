package com.proyecto.servicios.service;

import com.proyecto.servicios.client.GestoPagoServiceClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.model.gestopago.ProductoDTO;
import com.proyecto.servicios.model.gestopago.ProductoListResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.Impl.ProductoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProductoServiceTest {

    @Mock
    private GestoPagoServiceClient gestoPagoServiceClient;

    @Mock
    private GestoPagoTokenService tokenService;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ProductoServiceImpl productoService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        productoService = new ProductoServiceImpl(gestoPagoServiceClient, tokenService, productoRepository, redisTemplate);
        ReflectionTestUtils.setField(productoService, "idDistribuidor", 83);
        ReflectionTestUtils.setField(productoService, "codigoDispositivo", "GPS83-TPV-17");
    }

    @Test
    void testObtenerListaProductosRedisSuccess() {
        String xmlSample = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<RESPONSE>" +
                "    <MENSAJE>" +
                "        <TEXTO>Operacion realizada con exito</TEXTO>" +
                "    </MENSAJE>" +
                "    <PRODUCTOS>" +
                "        <producto servicio=\"AGUAKAN (Cancun)\" producto=\"Agua Cancun\" idServicio=\"101\" idProducto=\"5001\" idCatTipoServicio=\"2\" tipoFront=\"0\">" +
                "            <legend><![CDATA[Leyenda soporte.]]></legend>" +
                "        </producto>" +
                "    </PRODUCTOS>" +
                "</RESPONSE>";

        GestoPagoToken mockToken = new GestoPagoToken();
        mockToken.setToken("mock_jwt_token");

        when(tokenService.obtenerTokenActivo(anyInt(), anyString())).thenReturn(Optional.of(mockToken));
        when(gestoPagoServiceClient.getProductListRaw(anyString())).thenReturn(xmlSample);

        ProductoListResponse response = productoService.obtenerListaProductos();

        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertEquals("Operacion realizada con exito", response.getMensaje());
        assertEquals(1, response.getProductos().size());

        // Verificamos que se intento guardar en Redis
        verify(valueOperations, times(1)).set(anyString(), any(), any());
        // Verificamos que SIEMPRE se guarda tambien en PostgreSQL
        verify(productoRepository, times(1)).saveAll(any());
    }

    @Test
    void testObtenerListaProductosRedisFailureTriggersPostgresFallback() {
        String xmlSample = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<RESPONSE>" +
                "    <MENSAJE>" +
                "        <TEXTO>Operacion realizada con exito</TEXTO>" +
                "    </MENSAJE>" +
                "    <PRODUCTOS>" +
                "        <producto servicio=\"TELMEX\" producto=\"Pago Telmex\" idServicio=\"102\" idProducto=\"5002\" idCatTipoServicio=\"1\" tipoFront=\"0\">" +
                "            <legend><![CDATA[Leyenda]]></legend>" +
                "        </producto>" +
                "    </PRODUCTOS>" +
                "</RESPONSE>";

        GestoPagoToken mockToken = new GestoPagoToken();
        mockToken.setToken("mock_jwt_token");

        when(tokenService.obtenerTokenActivo(anyInt(), anyString())).thenReturn(Optional.of(mockToken));
        when(gestoPagoServiceClient.getProductListRaw(anyString())).thenReturn(xmlSample);
        // Simulamos fallo en Redis
        doThrow(new RuntimeException("Redis connection refused")).when(valueOperations).set(anyString(), any(), any());

        ProductoListResponse response = productoService.obtenerListaProductos();

        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertEquals(1, response.getProductos().size());

        // Verificamos que al fallar Redis se ejecuto el FALLBACK y guardo en PostgreSQL
        verify(productoRepository, times(1)).saveAll(any());
    }

    @Test
    void testObtenerProductosAlmacenadosDesdeRedis() {
        ProductoDTO dto = ProductoDTO.builder()
                .idProducto(100)
                .producto("Producto Redis")
                .idServicio(200)
                .servicio("Servicio Redis")
                .build();
        java.util.List<ProductoDTO> mockList = java.util.List.of(dto);

        when(valueOperations.get(anyString())).thenReturn(mockList);

        ProductoListResponse response = productoService.obtenerProductosAlmacenados();

        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertEquals("REDIS", response.getOrigen());
        assertEquals(1, response.getProductos().size());
        assertEquals("Producto Redis", response.getProductos().get(0).getProducto());

        verify(productoRepository, never()).findAll();
    }

    @Test
    void testObtenerProductosAlmacenadosDesdePostgresqlCuandoRedisVacio() {
        when(valueOperations.get(anyString())).thenReturn(null);

        com.proyecto.servicios.entity.gestopago.ProductoEntity entity = com.proyecto.servicios.entity.gestopago.ProductoEntity.builder()
                .idProducto(101)
                .producto("Producto DB")
                .idServicio(201)
                .servicio("Servicio DB")
                .build();

        when(productoRepository.findAll()).thenReturn(java.util.List.of(entity));

        ProductoListResponse response = productoService.obtenerProductosAlmacenados();

        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertEquals("POSTGRESQL", response.getOrigen());
        assertEquals(1, response.getProductos().size());
        assertEquals("Producto DB", response.getProductos().get(0).getProducto());
    }

    @Test
    void testObtenerProductosAlmacenadosVacioCuandoAmbosVacios() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(productoRepository.findAll()).thenReturn(java.util.Collections.emptyList());

        ProductoListResponse response = productoService.obtenerProductosAlmacenados();

        assertNotNull(response);
        assertEquals(1, response.getCodigo());
        assertEquals("NINGUNO", response.getOrigen());
        assertEquals("No hay productos almacenados en Redis ni en PostgreSQL", response.getMensaje());
        assertTrue(response.getProductos().isEmpty());
    }
}



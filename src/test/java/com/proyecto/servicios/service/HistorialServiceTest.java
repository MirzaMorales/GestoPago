package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.Cliente;
import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.EstatusTransaccion;
import com.proyecto.servicios.entity.TransaccionPago;
import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CompraNoEncontradaException;
import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.model.EstadoCompra;
import com.proyecto.servicios.model.request.HistorialRequest;
import com.proyecto.servicios.model.response.CompraResponse;
import com.proyecto.servicios.model.response.PaginaResponse;
import com.proyecto.servicios.repositorys.TransaccionPagoRepository;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.Impl.HistorialServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistorialServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TransaccionPagoRepository transaccionRepository;
    @Mock private ProductoRepository productoRepository;
    @InjectMocks private HistorialServiceImpl service;

    private TransaccionPago compra;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        Usuario usuario = Usuario.builder().id(1L).correo("cliente@example.com").cliente(cliente).build();
        when(usuarioRepository.findByCorreo("cliente@example.com")).thenReturn(Optional.of(usuario));

        Cuenta cuenta = Cuenta.builder().numeroCuenta("1234567890123456").build();
        compra = TransaccionPago.builder().id(99L).cuenta(cuenta).idProducto(209).idServicio(79)
                .telefonoReferencia("5512345678").monto(new BigDecimal("150.00"))
                .gpComision(new BigDecimal("3.00")).gpIdTx("GP-1").gpNumAutorizacion("AUT-1")
                .estatus(EstatusTransaccion.EXITOSA)
                .fechaResolucion(OffsetDateTime.parse("2026-11-10T10:00:00-06:00")).build();
        ProductoEntity producto = new ProductoEntity();
        producto.setIdProducto(209);
        producto.setProducto("Telcel $150");
        when(productoRepository.findAllById(any())).thenReturn(List.of(producto));
        when(productoRepository.findById(209)).thenReturn(Optional.of(producto));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Collection<EstatusTransaccion>> estatusCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    private void historialDevuelve(TransaccionPago... compras) {
        when(transaccionRepository.buscarHistorial(eq(10L), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(compras), PageRequest.of(0, 20), compras.length));
    }

    @Test
    void sinFiltrosListaTodasLasComprasDelClienteConProductoYCuentaEnmascarada() {
        historialDevuelve(compra);

        PaginaResponse<CompraResponse> r = service.listar("cliente@example.com", 0, 20);

        assertEquals(1, r.getTotalElementos());
        CompraResponse c = r.getContenido().get(0);
        assertEquals(99L, c.getIdTransaccion());
        assertEquals("Telcel $150", c.getProducto());
        assertEquals("************3456", c.getCuenta());
        assertEquals("EXITOSA", c.getEstatus());
        assertEquals(EstadoCompra.EXITOSA, c.getEstadoCompra());
        assertNull(c.getMotivo());
        assertEquals("GP-1", c.getIdTxGestoPago());

        var estatus = estatusCaptor();
        verify(transaccionRepository).buscarHistorial(eq(10L), estatus.capture(), any(), any(), any());
        assertEquals(EnumSet.allOf(EstatusTransaccion.class), Set.copyOf(estatus.getValue()));
    }

    @Test
    void cadaFiltroDeEstadoBuscaSusEstatusInternos() {
        historialDevuelve();
        for (var caso : List.of(
                List.of("EXITOSA", Set.of(EstatusTransaccion.EXITOSA)),
                List.of("RECHAZADA", Set.of(EstatusTransaccion.FALLIDA)),
                List.of("PENDIENTE", Set.of(EstatusTransaccion.PENDIENTE, EstatusTransaccion.EN_REVISION)))) {
            clearInvocations(transaccionRepository);
            service.listar("cliente@example.com", HistorialRequest.builder().estado((String) caso.get(0)).build());

            var estatus = estatusCaptor();
            verify(transaccionRepository).buscarHistorial(eq(10L), estatus.capture(), any(), any(), any());
            assertEquals(caso.get(1), Set.copyOf(estatus.getValue()), (String) caso.get(0));
        }
    }

    @Test
    void elRangoDeFechasIncluyeDiasCompletosEnHoraDeMexico() {
        historialDevuelve();

        service.listar("cliente@example.com", HistorialRequest.builder()
                .fechaInicio(LocalDate.of(2026, 11, 1)).fechaFin(LocalDate.of(2026, 11, 23)).build());

        ArgumentCaptor<OffsetDateTime> desde = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> hasta = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(transaccionRepository).buscarHistorial(eq(10L), any(), desde.capture(), hasta.capture(), any());
        assertEquals(OffsetDateTime.parse("2026-11-01T00:00-06:00").toInstant(), desde.getValue().toInstant());
        assertEquals(OffsetDateTime.parse("2026-11-24T00:00-06:00").toInstant(), hasta.getValue().toInstant());
    }

    @Test
    void rangoDeFechasInvertidoSeRechaza() {
        assertThrows(ValidacionNegocioException.class, () -> service.listar("cliente@example.com",
                HistorialRequest.builder().fechaInicio(LocalDate.of(2026, 11, 23)).fechaFin(LocalDate.of(2026, 11, 1)).build()));
    }

    @Test
    void unaCompraRechazadaMuestraElCodigoDeGestoPagoSinDetallesInternos() {
        compra.setEstatus(EstatusTransaccion.FALLIDA);
        compra.setDetalle("GestoPago rechazó la transacción (código 55)");
        historialDevuelve(compra);

        CompraResponse c = service.listar("cliente@example.com", 0, 20).getContenido().get(0);

        assertEquals(EstadoCompra.RECHAZADA, c.getEstadoCompra());
        assertEquals("GestoPago rechazó la compra (código 55). No se hizo ningún cargo", c.getMotivo());
    }

    @Test
    void unaCompraEnRevisionSeMuestraPendienteConElImporteRetenido() {
        compra.setEstatus(EstatusTransaccion.EN_REVISION);
        compra.setDetalle("sendTx: GESTOPAGO_NO_DISPONIBLE; pendiente de confirmTx");
        historialDevuelve(compra);

        CompraResponse c = service.listar("cliente@example.com", 0, 20).getContenido().get(0);

        assertEquals(EstadoCompra.PENDIENTE, c.getEstadoCompra());
        assertFalse(c.getMotivo().contains("GESTOPAGO_NO_DISPONIBLE"));
        assertTrue(c.getMotivo().contains("retenido"));
    }

    @Test
    void rechazaParametrosDePaginacionInvalidos() {
        assertThrows(ValidacionNegocioException.class, () -> service.listar("cliente@example.com", -1, 20));
        assertThrows(ValidacionNegocioException.class, () -> service.listar("cliente@example.com", 0, 0));
        assertThrows(ValidacionNegocioException.class, () -> service.listar("cliente@example.com", 0, 51));
    }

    @Test
    void detalleDevuelveLaCompraDelClienteEnCualquierEstado() {
        when(transaccionRepository.findByIdAndCuentaClienteId(99L, 10L)).thenReturn(Optional.of(compra));
        assertEquals("Telcel $150", service.detalle("cliente@example.com", 99L).getProducto());

        compra.setEstatus(EstatusTransaccion.FALLIDA);
        assertEquals(EstadoCompra.RECHAZADA, service.detalle("cliente@example.com", 99L).getEstadoCompra());
    }

    @Test
    void detalleDeCompraAjenaOInexistenteDaNoEncontrada() {
        when(transaccionRepository.findByIdAndCuentaClienteId(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThrows(CompraNoEncontradaException.class, () -> service.detalle("cliente@example.com", 5L));
    }

    @Test
    void usuarioSinClienteNoTieneHistorial() {
        Usuario admin = Usuario.builder().id(2L).correo("admin@example.com").build();
        when(usuarioRepository.findByCorreo("admin@example.com")).thenReturn(Optional.of(admin));

        assertThrows(ValidacionNegocioException.class, () -> service.listar("admin@example.com", 0, 20));
    }

    @Test
    void estadoDeUnaCompraEnRevisionEsPendiente() {
        compra.setEstatus(EstatusTransaccion.EN_REVISION);
        when(transaccionRepository.findByIdAndCuentaClienteId(99L, 10L)).thenReturn(Optional.of(compra));

        assertEquals(EstadoCompra.PENDIENTE, service.estado("cliente@example.com", 99L).getEstadoCompra());
    }

    @Test
    void estadoDeUnaCompraFallidaEsRechazadaYDeUnaAjenaNoSeEncuentra() {
        compra.setEstatus(EstatusTransaccion.FALLIDA);
        when(transaccionRepository.findByIdAndCuentaClienteId(99L, 10L)).thenReturn(Optional.of(compra));
        when(transaccionRepository.findByIdAndCuentaClienteId(5L, 10L)).thenReturn(Optional.empty());

        assertEquals(EstadoCompra.RECHAZADA, service.estado("cliente@example.com", 99L).getEstadoCompra());
        assertThrows(CompraNoEncontradaException.class, () -> service.estado("cliente@example.com", 5L));
    }
}

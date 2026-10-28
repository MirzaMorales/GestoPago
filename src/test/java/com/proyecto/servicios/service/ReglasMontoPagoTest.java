package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ReglasMontoPagoTest {

    private ReglasMontoPago reglas;

    @BeforeEach
    void setUp() {
        reglas = new ReglasMontoPago();
        ReflectionTestUtils.setField(reglas, "montoMaximo", new BigDecimal("20000.00"));
    }

    private PagoServicioRequest request(String monto, String referencia) {
        return PagoServicioRequest.builder().numeroCuenta("1234567890123456").pin("1234")
                .monto(new BigDecimal(monto)).telefonoReferencia(referencia)
                .idServicio(133).idProducto(582).tipoFront(1).build();
    }

    private ProductoEntity producto(Integer tipoFront, Integer catTipo, String precio) {
        return ProductoEntity.builder().idProducto(582).idServicio(133).tipoFront(tipoFront).idCatTipoServicio(catTipo)
                .precio(precio == null ? null : new BigDecimal(precio)).build();
    }

    private CodigoErrorPago errorDe(PagoServicioRequest r, ProductoEntity p) {
        return assertThrows(PagoException.class, () -> reglas.montoADebitar(r, p)).getError();
    }

    @Test
    void recarga_montoIgualAlPrecio_debitaElPrecioDelCatalogo() {
        assertEquals(new BigDecimal("100.00"), reglas.montoADebitar(request("100.00", "5555555551"), producto(1, 2, "100.0")));
    }

    @Test
    void recarga_montoManipulado_menorOMayorAlPrecio_seRechaza() {
        ProductoEntity p = producto(1, 2, "100.0");

        assertEquals(CodigoErrorPago.MONTO_NO_COINCIDE, errorDe(request("1.00", "5555555551"), p));
        assertEquals(CodigoErrorPago.MONTO_NO_COINCIDE, errorDe(request("100.01", "5555555551"), p));
    }

    @Test
    void recarga_sinPrecioEnElCatalogo_seRechaza() {
        assertEquals(CodigoErrorPago.PRECIO_NO_DISPONIBLE, errorDe(request("100.00", "5555555551"), producto(1, 2, null)));
    }

    @Test
    void recarga_telefonoDebeTenerDiezDigitos() {
        ProductoEntity p = producto(1, 2, "100.0");

        for (String telefono : new String[]{"555555555", "55555555512", "55555ABC51", "#111111111"}) {
            assertEquals(CodigoErrorPago.REFERENCIA_INVALIDA, errorDe(request("100.00", telefono), p), telefono);
        }
    }

    @Test
    void productoCanjeable_noExigeTelefonoPeroSiElPrecio() {
        ProductoEntity amazon = producto(1, 10, "100.0");

        assertEquals(new BigDecimal("100.00"), reglas.montoADebitar(request("100.00", "REF-LIBRE"), amazon));
        assertEquals(CodigoErrorPago.MONTO_NO_COINCIDE, errorDe(request("90.00", "REF-LIBRE"), amazon));
    }

    @Test
    void pagoDeServicio_montoLibreHastaElLimite() {
        ProductoEntity servicio = producto(2, 5, "10.0");

        assertEquals(new BigDecimal("601.99"), reglas.montoADebitar(request("601.99", "25720381"), servicio));
        assertEquals(new BigDecimal("20000.00"), reglas.montoADebitar(request("20000.00", "25720381"), servicio));
        assertEquals(CodigoErrorPago.MONTO_EXCEDE_LIMITE, errorDe(request("20000.01", "25720381"), servicio));
    }

    @Test
    void tiposNoSoportados_seRechazan() {
        for (Integer tipo : new Integer[]{4, 5, 30, 31, 40, null}) {
            assertEquals(CodigoErrorPago.TIPO_FRONT_NO_SOPORTADO,
                    errorDe(request("100.00", "5555555551"), producto(tipo, 1, "100.0")), String.valueOf(tipo));
        }
    }
}

package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.GestoPagoToken;

import java.util.Optional;

public interface GestoPagoTokenService {

    /** Renueva el token solo si no existe o tiene más de la edad máxima (el proveedor exige un token por día). */
    void renovarToken();

    /** Renueva el token sin importar su edad; se usa únicamente cuando el proveedor responde 403 (token vencido). */
    void renovarTokenForzado();

    Optional<GestoPagoToken> obtenerTokenActivo(Integer idDistribuidor, String codigoDispositivo);
}

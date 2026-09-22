package com.proyecto.servicios.service;

import com.proyecto.servicios.model.gestopago.ProductoListResponse;

public interface ProductoService {

    ProductoListResponse obtenerListaProductos();

    ProductoListResponse obtenerListaProductos(String token);

    ProductoListResponse obtenerProductosAlmacenados();
}


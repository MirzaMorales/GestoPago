package com.proyecto.servicios.model.response;

/** Asentamiento del catálogo SEPOMEX: lo necesario para llenar el domicilio sin escribirlo a mano. */
public record ColoniaResponse(
        String codigoPostal,
        String colonia,
        String tipoAsentamiento,
        String municipio,
        String estado,
        String ciudad
) {
}

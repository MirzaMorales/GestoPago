package com.proyecto.servicios.entity.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Asentamiento del catálogo nacional de códigos postales de SEPOMEX (solo lectura para la API). */
@Entity
@Table(name = "cat_codigos_postales")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodigoPostal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_postal", nullable = false, length = 5)
    private String codigoPostal;

    /** Nombre de la colonia, fraccionamiento, barrio, etc. */
    @Column(name = "asentamiento", nullable = false, length = 150)
    private String asentamiento;

    @Column(name = "tipo_asentamiento", length = 60)
    private String tipoAsentamiento;

    @Column(name = "municipio", nullable = false, length = 100)
    private String municipio;

    @Column(name = "estado", nullable = false, length = 100)
    private String estado;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Column(name = "zona", length = 20)
    private String zona;
}

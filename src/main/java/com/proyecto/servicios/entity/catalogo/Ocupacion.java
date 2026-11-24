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

/** Ocupación del catálogo (agrupada por división del SINCO 2019). Solo lectura para la API. */
@Entity
@Table(name = "cat_ocupaciones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ocupacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(nullable = false, length = 120)
    private String grupo;

    @Column(nullable = false)
    private Integer orden;
}

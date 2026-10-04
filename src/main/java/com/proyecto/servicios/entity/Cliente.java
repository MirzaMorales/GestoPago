package com.proyecto.servicios.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(name = "segundo_nombre", length = 50)
    private String segundoNombre;

    @Column(name = "apellido_paterno", nullable = false, length = 50)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", nullable = false, length = 50)
    private String apellidoMaterno;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(nullable = false, unique = true, length = 18)
    private String curp;

    @Column(nullable = false, unique = true, length = 13)
    private String rfc;

    @Column(nullable = false, length = 20)
    private String sexo;

    @Column(nullable = false, length = 50)
    private String nacionalidad;

    @Column(name = "estado_civil", nullable = false, length = 30)
    private String estadoCivil;

    @Column(nullable = false, unique = true, length = 100)
    private String correo;

    @Column(name = "telefono_movil", nullable = false, length = 10)
    private String telefonoMovil;

    @Column(name = "telefono_alternativo", length = 10)
    private String telefonoAlternativo;

    @Column(nullable = false, length = 100)
    private String ocupacion;

    @Column(nullable = false, length = 100)
    private String empresa;

    @Column(name = "ingreso_mensual", nullable = false, precision = 15, scale = 2)
    private BigDecimal ingresoMensual;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "domicilio_id", referencedColumnName = "id", nullable = false, unique = true)
    private Domicilio domicilio;

    @Column(nullable = false)
    private Boolean activo;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Cuenta> cuentas = new ArrayList<>();

    @OneToOne(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Usuario usuario;

    @PrePersist
    protected void onCreate() {
        if (this.activo == null) {
            this.activo = true;
        }
        if (this.nacionalidad == null || this.nacionalidad.isBlank()) {
            this.nacionalidad = "Mexicana";
        }
        this.fechaCreacion = OffsetDateTime.now();
        this.fechaActualizacion = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = OffsetDateTime.now();
    }
}

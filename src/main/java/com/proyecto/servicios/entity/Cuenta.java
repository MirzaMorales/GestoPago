package com.proyecto.servicios.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cuentas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_cuenta", nullable = false, unique = true, length = 16)
    private String numeroCuenta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstatusCuenta estatus;

    @Column(name = "fecha_apertura", nullable = false, updatable = false)
    private OffsetDateTime fechaApertura;

    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        if (this.saldo == null) {
            this.saldo = BigDecimal.ZERO;
        }
        if (this.estatus == null) {
            this.estatus = EstatusCuenta.ACTIVA;
        }
        this.fechaApertura = OffsetDateTime.now();
        this.fechaActualizacion = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = OffsetDateTime.now();
    }
}

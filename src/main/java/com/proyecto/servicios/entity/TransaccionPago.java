package com.proyecto.servicios.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transacciones_pago")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransaccionPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    @Column(name = "id_producto", nullable = false)
    private Integer idProducto;

    @Column(name = "id_servicio", nullable = false)
    private Integer idServicio;

    @Column(name = "telefono_referencia", nullable = false, length = 100)
    private String telefonoReferencia;

    /** Monto descontado de la cuenta del cliente. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstatusTransaccion estatus;

    /** Llave enviada por el cliente (header Idempotency-Key); única por cuenta. */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** SHA-256 de los datos de la solicitud, para detectar la reutilización de una llave con otra solicitud. */
    @Column(name = "huella_solicitud", length = 64)
    private String huellaSolicitud;

    /** Unique Payment Code enviado al proveedor; el proveedor lo usa para rechazar duplicados. */
    @Column(length = 15)
    private String upc;

    /** JSON exacto enviado a sendTx; confirmTx debe reutilizarlo sin cambios. */
    @Column(name = "payload_enviado", columnDefinition = "TEXT")
    private String payloadEnviado;

    @Column(name = "fecha_envio")
    private OffsetDateTime fechaEnvio;

    @Column(name = "fecha_resolucion")
    private OffsetDateTime fechaResolucion;

    @Column(name = "gp_id_tx", length = 50)
    private String gpIdTx;

    @Column(name = "gp_num_autorizacion", length = 60)
    private String gpNumAutorizacion;

    @Column(name = "gp_comision", precision = 15, scale = 2)
    private BigDecimal gpComision;

    @Column(length = 500)
    private String detalle;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = OffsetDateTime.now();
        this.fechaActualizacion = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = OffsetDateTime.now();
    }
}

package com.proyecto.servicios.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Bitácora de intercambios con GestoPago (sendTx / confirmTx). El proveedor exige conservar
 * lo enviado y lo recibido para cualquier revisión o aclaración. El PIN/folio de productos
 * canjeables se enmascara antes de guardar la respuesta.
 */
@Entity
@Table(name = "gestopago_tx_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GestoPagoTxLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaccion_id", nullable = false)
    private Long transaccionId;

    @Column(nullable = false, length = 20)
    private String operacion;

    @Column(name = "payload_enviado", columnDefinition = "TEXT")
    private String payloadEnviado;

    @Column(columnDefinition = "TEXT")
    private String respuesta;

    @Column(nullable = false, length = 40)
    private String resultado;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime fecha;

    @PrePersist
    protected void onCreate() {
        this.fecha = OffsetDateTime.now();
    }
}

package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@XmlRootElement(name = "producto")
@XmlAccessorType(XmlAccessType.FIELD)
public class ProductoDTO {

    @XmlAttribute(name = "idProducto")
    private Integer idProducto;

    @XmlAttribute(name = "producto")
    private String producto;

    @XmlAttribute(name = "idServicio")
    private Integer idServicio;

    @XmlAttribute(name = "servicio")
    private String servicio;

    @XmlAttribute(name = "idCatTipoServicio")
    private Integer idCatTipoServicio;

    @XmlAttribute(name = "tipoFront")
    private Integer tipoFront;

    /** tipoFront 1: precio final a cobrar. tipoFront 2: comisión de la transacción. */
    @XmlAttribute(name = "precio")
    private BigDecimal precio;

    @XmlElement(name = "legend")
    private String legend;

    /** Logo público del servicio (PNG) en GestoPago; se identifica por idServicio y no requiere token. */
    private static final String LOGO_BASE_URL = "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/";

    /** true si el producto se puede comprar desde esta API: tipoFront 1 (con precio) o 2 (importe libre). */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public boolean isDisponibleParaCompra() {
        if (Integer.valueOf(1).equals(tipoFront)) {
            return precio != null && precio.signum() > 0;
        }
        return Integer.valueOf(2).equals(tipoFront);
    }

    /** true si el cliente captura el importe (pago de servicios); false si el precio es fijo. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public boolean isMontoLibre() {
        return Integer.valueOf(2).equals(tipoFront);
    }

    /** Categoría para organizar el catálogo, derivada del tipo de producto que define el proveedor. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getCategoria() {
        if (Integer.valueOf(1).equals(tipoFront)) {
            return "Recargas y tarjetas";
        }
        if (Integer.valueOf(2).equals(tipoFront)) {
            return "Pago de servicios";
        }
        return "Otros";
    }

    /** Calculado a partir de idServicio: no se guarda en Redis ni en la base, así no queda desactualizado. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getLogoUrl() {
        return idServicio != null ? LOGO_BASE_URL + idServicio + ".png" : null;
    }
}

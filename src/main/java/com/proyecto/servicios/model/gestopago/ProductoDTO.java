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

    /** Calculado a partir de idServicio: no se guarda en Redis ni en la base, así no queda desactualizado. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getLogoUrl() {
        return idServicio != null ? LOGO_BASE_URL + idServicio + ".png" : null;
    }
}

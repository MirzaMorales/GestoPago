package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.catalogo.CodigoPostal;
import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.model.request.DomicilioRequest;
import com.proyecto.servicios.model.response.ColoniaResponse;
import com.proyecto.servicios.repositorys.catalogo.CodigoPostalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Catálogo nacional de códigos postales (SEPOMEX): listas para el formulario de domicilio y validación
 * en el servidor de que el código postal, la colonia, el municipio y el estado existen y corresponden entre sí.
 */
@Slf4j
@Service
public class CatalogoDomicilioService {

    public static final String MENSAJE_DOMICILIO_INVALIDO =
            "El domicilio no corresponde al catálogo de códigos postales: revisa el código postal y la colonia";

    private final CodigoPostalRepository repository;

    /** Se recuerda en memoria una vez confirmado que el catálogo tiene datos (no cambia en ejecución). */
    private volatile boolean catalogoCargado;

    public CatalogoDomicilioService(CodigoPostalRepository repository) {
        this.repository = repository;
    }

    public List<String> estados() {
        return repository.findEstados();
    }

    public List<String> municipios(String estado) {
        return repository.findMunicipios(estado.trim());
    }

    public List<ColoniaResponse> colonias(String estado, String municipio) {
        return repository.findByEstadoAndMunicipioOrderByAsentamientoAsc(estado.trim(), municipio.trim())
                .stream().map(CatalogoDomicilioService::aRespuesta).toList();
    }

    public List<ColoniaResponse> porCodigoPostal(String codigoPostal) {
        return repository.findByCodigoPostalOrderByAsentamientoAsc(codigoPostal)
                .stream().map(CatalogoDomicilioService::aRespuesta).toList();
    }

    /**
     * Rechaza un domicilio cuya combinación de código postal, colonia, municipio y estado no exista en SEPOMEX.
     * Si el catálogo aún no se cargó en la base, no bloquea el registro y lo deja registrado en el log.
     */
    public void validar(DomicilioRequest domicilio) {
        if (domicilio == null || !estaCargado()) {
            return;
        }
        boolean existe = repository.existsByCodigoPostalAndAsentamientoIgnoreCaseAndMunicipioIgnoreCaseAndEstadoIgnoreCase(
                limpiar(domicilio.getCodigoPostal()), limpiar(domicilio.getColonia()),
                limpiar(domicilio.getMunicipio()), limpiar(domicilio.getEstado()));
        if (!existe) {
            throw new ValidacionNegocioException(MENSAJE_DOMICILIO_INVALIDO);
        }
    }

    private boolean estaCargado() {
        if (!catalogoCargado) {
            catalogoCargado = repository.count() > 0;
            if (!catalogoCargado) {
                log.warn("El catálogo de códigos postales está vacío: el domicilio no se valida contra SEPOMEX");
            }
        }
        return catalogoCargado;
    }

    private static String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static ColoniaResponse aRespuesta(CodigoPostal c) {
        return new ColoniaResponse(c.getCodigoPostal(), c.getAsentamiento(), c.getTipoAsentamiento(),
                c.getMunicipio(), c.getEstado(), c.getCiudad());
    }
}

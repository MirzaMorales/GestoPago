package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.model.response.OcupacionResponse;
import com.proyecto.servicios.repositorys.catalogo.OcupacionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** Catálogo de ocupaciones: lista para el formulario y validación en el servidor de que la elegida existe. */
@Slf4j
@Service
public class CatalogoOcupacionService {

    public static final String MENSAJE_OCUPACION_INVALIDA = "Elige una ocupación del catálogo";

    private final OcupacionRepository repository;
    private volatile boolean catalogoCargado;

    public CatalogoOcupacionService(OcupacionRepository repository) {
        this.repository = repository;
    }

    public List<OcupacionResponse> listar() {
        return repository.findAllByOrderByOrdenAscNombreAsc().stream()
                .map(o -> new OcupacionResponse(o.getNombre(), o.getGrupo()))
                .toList();
    }

    /** Rechaza una ocupación que no esté en el catálogo (si el catálogo está vacío no bloquea y lo registra). */
    public void validar(String ocupacion) {
        if (!estaCargado()) {
            return;
        }
        if (ocupacion == null || !repository.existsByNombreIgnoreCase(ocupacion.trim())) {
            throw new ValidacionNegocioException(MENSAJE_OCUPACION_INVALIDA);
        }
    }

    private boolean estaCargado() {
        if (!catalogoCargado) {
            catalogoCargado = repository.count() > 0;
            if (!catalogoCargado) {
                log.warn("El catálogo de ocupaciones está vacío: la ocupación no se valida");
            }
        }
        return catalogoCargado;
    }
}

package com.proyecto.servicios.repositorys.catalogo;

import com.proyecto.servicios.entity.catalogo.Ocupacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OcupacionRepository extends JpaRepository<Ocupacion, Integer> {

    List<Ocupacion> findAllByOrderByOrdenAscNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);
}

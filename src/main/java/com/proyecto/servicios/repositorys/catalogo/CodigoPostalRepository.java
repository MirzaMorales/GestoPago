package com.proyecto.servicios.repositorys.catalogo;

import com.proyecto.servicios.entity.catalogo.CodigoPostal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CodigoPostalRepository extends JpaRepository<CodigoPostal, Long> {

    @Query("select distinct c.estado from CodigoPostal c order by c.estado")
    List<String> findEstados();

    @Query("select distinct c.municipio from CodigoPostal c where c.estado = :estado order by c.municipio")
    List<String> findMunicipios(@Param("estado") String estado);

    List<CodigoPostal> findByEstadoAndMunicipioOrderByAsentamientoAsc(String estado, String municipio);

    List<CodigoPostal> findByCodigoPostalOrderByAsentamientoAsc(String codigoPostal);

    boolean existsByCodigoPostalAndAsentamientoIgnoreCaseAndMunicipioIgnoreCaseAndEstadoIgnoreCase(
            String codigoPostal, String asentamiento, String municipio, String estado);
}

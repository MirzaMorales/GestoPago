package com.proyecto.servicios.repositorys;

import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.EstatusCuenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);

    boolean existsByNumeroCuenta(String numeroCuenta);

    List<Cuenta> findByEstatus(EstatusCuenta estatus);

    @Query("SELECT c FROM Cuenta c WHERE c.estatus = :estatus AND c.cliente.activo = true")
    List<Cuenta> findByEstatusAndActiveClient(@Param("estatus") EstatusCuenta estatus);

    List<Cuenta> findByClienteId(Long clienteId);
}

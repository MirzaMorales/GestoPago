package com.proyecto.servicios.repositorys;

import com.proyecto.servicios.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    Optional<Cliente> findByCurp(String curp);

    Optional<Cliente> findByRfc(String rfc);

    Optional<Cliente> findByCorreo(String correo);

    boolean existsByCurp(String curp);

    boolean existsByRfc(String rfc);

    boolean existsByCorreo(String correo);

    List<Cliente> findByActivo(Boolean activo);

    @Query("SELECT c FROM Cliente c WHERE c.fechaCreacion BETWEEN :fechaInicio AND :fechaFin")
    List<Cliente> findClientesByRangoFechas(
            @Param("fechaInicio") OffsetDateTime fechaInicio,
            @Param("fechaFin") OffsetDateTime fechaFin
    );

    @Query("SELECT c FROM Cliente c JOIN c.cuentas cnt WHERE cnt.numeroCuenta = :numeroCuenta")
    Optional<Cliente> findByNumeroCuenta(@Param("numeroCuenta") String numeroCuenta);
}

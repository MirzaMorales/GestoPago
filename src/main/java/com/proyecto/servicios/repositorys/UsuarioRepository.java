package com.proyecto.servicios.repositorys;

import com.proyecto.servicios.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    @Query("SELECT u FROM Usuario u JOIN FETCH u.cliente c WHERE u.correo = :correo AND u.activo = true AND c.activo = true")
    Optional<Usuario> findActiveByCorreo(@Param("correo") String correo);

    boolean existsByCorreo(String correo);

    Optional<Usuario> findByClienteId(Long clienteId);
}

package com.proyecto.servicios.repositorys;

import com.proyecto.servicios.entity.Cliente;
import com.proyecto.servicios.entity.Cuenta;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class ClienteSpecification {

    public static Specification<Cliente> buscarConFiltros(
            String curp,
            String rfc,
            String correo,
            String nombre,
            String apellido,
            Boolean activo,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String numeroCuenta
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (curp != null && !curp.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.upper(root.get("curp")),
                        curp.trim().toUpperCase()
                ));
            }

            if (rfc != null && !rfc.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.upper(root.get("rfc")),
                        rfc.trim().toUpperCase()
                ));
            }

            if (correo != null && !correo.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("correo")),
                        "%" + correo.trim().toLowerCase() + "%"
                ));
            }

            if (nombre != null && !nombre.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("nombre")),
                        "%" + nombre.trim().toLowerCase() + "%"
                ));
            }

            if (apellido != null && !apellido.isBlank()) {
                Predicate p1 = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("apellidoPaterno")),
                        "%" + apellido.trim().toLowerCase() + "%"
                );
                Predicate p2 = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("apellidoMaterno")),
                        "%" + apellido.trim().toLowerCase() + "%"
                );
                predicates.add(criteriaBuilder.or(p1, p2));
            }

            if (activo != null) {
                predicates.add(criteriaBuilder.equal(root.get("activo"), activo));
            }

            if (fechaInicio != null) {
                OffsetDateTime startDateTime = fechaInicio.atStartOfDay().atOffset(ZoneOffset.UTC);
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("fechaCreacion"), startDateTime));
            }

            if (fechaFin != null) {
                OffsetDateTime endDateTime = fechaFin.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("fechaCreacion"), endDateTime));
            }

            if (numeroCuenta != null && !numeroCuenta.isBlank()) {
                Join<Cliente, Cuenta> cuentaJoin = root.join("cuentas", JoinType.INNER);
                predicates.add(criteriaBuilder.equal(cuentaJoin.get("numeroCuenta"), numeroCuenta.trim()));
            }

            query.distinct(true);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}

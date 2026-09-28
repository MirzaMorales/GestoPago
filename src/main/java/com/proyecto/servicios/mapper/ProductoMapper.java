package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.model.gestopago.ProductoDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    ProductoDTO toDto(ProductoEntity entity);

    List<ProductoDTO> toDtoList(List<ProductoEntity> entities);

    @Mapping(target = "fechaActualizacion", expression = "java(java.time.LocalDateTime.now())")
    ProductoEntity toEntity(ProductoDTO dto);

    List<ProductoEntity> toEntityList(List<ProductoDTO> dtos);

    @Mapping(target = "fechaActualizacion", expression = "java(java.time.LocalDateTime.now())")
    void updateEntity(ProductoDTO dto, @MappingTarget ProductoEntity entity);
}

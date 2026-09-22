package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoServiceClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.model.gestopago.ProductoDTO;
import com.proyecto.servicios.model.gestopago.ProductoListResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.ProductoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ProductoServiceImpl implements ProductoService {


    private static final String REDIS_PRODUCTOS_KEY = "gestopago:productos";

    private final GestoPagoServiceClient gestoPagoServiceClient;
    private final GestoPagoTokenService tokenService;
    private final ProductoRepository productoRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${gestopago.auth.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo}")
    private String codigoDispositivo;

    public ProductoServiceImpl(GestoPagoServiceClient gestoPagoServiceClient,
                               GestoPagoTokenService tokenService,
                               ProductoRepository productoRepository,
                               RedisTemplate<String, Object> redisTemplate) {
        this.gestoPagoServiceClient = gestoPagoServiceClient;
        this.tokenService = tokenService;
        this.productoRepository = productoRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public ProductoListResponse obtenerListaProductos() {
        Optional<GestoPagoToken> tokenOptional = tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo);
        if (tokenOptional.isEmpty()) {
            log.error("No se encontro un token activo para idDistribuidor={} y codigoDispositivo={}",
                    idDistribuidor, codigoDispositivo);
            return ProductoListResponse.builder()
                    .codigo(1)
                    .mensaje("No se pudo obtener un token activo de GestoPago")
                    .productos(new ArrayList<>())
                    .build();
        }

        String token = tokenOptional.get().getToken();
        return obtenerListaProductos(token);
    }

    @Override
    public ProductoListResponse obtenerListaProductos(String token) {
        try {
            log.info("Consultando getProductList en GestoPago API");
            String authHeader = token.startsWith("Bearer ") ? token : "Bearer " + token;
            String rawXml = gestoPagoServiceClient.getProductListRaw(authHeader);

            if (rawXml == null || rawXml.isBlank()) {
                log.warn("Respuesta vacia de GestoPago getProductList");
                return ProductoListResponse.builder()
                        .codigo(1)
                        .mensaje("La respuesta de GestoPago fue vacia")
                        .productos(new ArrayList<>())
                        .build();
            }

            // Parsear XML de manera flexible utilizando DOM Parser
            List<ProductoDTO> listaProductos = new ArrayList<>();
            String mensajeTexto = "Operacion realizada con exito";

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(rawXml)));

            // Extraer mensaje (<TEXTO> o <TEXT>)
            NodeList textoNodes = doc.getElementsByTagName("TEXTO");
            if (textoNodes.getLength() == 0) {
                textoNodes = doc.getElementsByTagName("TEXT");
            }
            if (textoNodes.getLength() > 0) {
                mensajeTexto = textoNodes.item(0).getTextContent();
            }

            // Extraer nodos de productos (<producto> o <PRODUCTO>)
            NodeList productoNodes = doc.getElementsByTagName("producto");
            if (productoNodes.getLength() == 0) {
                productoNodes = doc.getElementsByTagName("PRODUCTO");
            }

            for (int i = 0; i < productoNodes.getLength(); i++) {
                Element el = (Element) productoNodes.item(i);
                ProductoDTO dto = ProductoDTO.builder()
                        .idProducto(parseAttrInt(el, "idProducto"))
                        .producto(getAttrString(el, "producto"))
                        .idServicio(parseAttrInt(el, "idServicio"))
                        .servicio(getAttrString(el, "servicio"))
                        .idCatTipoServicio(parseAttrInt(el, "idCatTipoServicio"))
                        .tipoFront(parseAttrInt(el, "tipoFront"))
                        .legend(getLegendContent(el))
                        .build();
                listaProductos.add(dto);
            }

            log.info("Lista de productos obtenida correctamente de la API. Total productos: {}", listaProductos.size());

            // Almacenar productos: Intento primario en Redis, con Fallback a PostgreSQL
            guardarProductosConFallback(listaProductos);

            return ProductoListResponse.builder()
                    .codigo(0)
                    .mensaje(mensajeTexto)
                    .origen("GESTOPAGO_API")
                    .productos(listaProductos)
                    .build();

        } catch (Exception e) {
            log.error("Error al obtener la lista de productos de GestoPago: {}", e.getMessage(), e);
            return ProductoListResponse.builder()
                    .codigo(1)
                    .mensaje("Error al procesar la lista de productos: " + e.getMessage())
                    .productos(new ArrayList<>())
                    .build();
        }
    }

    @Override
    public ProductoListResponse obtenerProductosAlmacenados() {
        // 1. Intentar consultar en Redis
        try {
            log.info("Consultando productos almacenados en Redis (key='{}')...", REDIS_PRODUCTOS_KEY);
            Object cachedData = redisTemplate.opsForValue().get(REDIS_PRODUCTOS_KEY);

            if (cachedData != null) {
                List<ProductoDTO> productos = null;
                if (cachedData instanceof List) {
                    List<?> rawList = (List<?>) cachedData;
                    if (!rawList.isEmpty()) {
                        ObjectMapper mapper = new ObjectMapper();
                        productos = mapper.convertValue(rawList, new TypeReference<List<ProductoDTO>>() {});
                    } else {
                        productos = new ArrayList<>();
                    }
                }

                if (productos != null && !productos.isEmpty()) {
                    log.info("Productos obtenidos correctamente desde REDIS. Total: {}", productos.size());
                    return ProductoListResponse.builder()
                            .codigo(0)
                            .mensaje("Operacion realizada con exito")
                            .origen("REDIS")
                            .productos(productos)
                            .build();
                }
            }
            log.info("Redis esta vacio o no contiene la clave '{}'", REDIS_PRODUCTOS_KEY);
        } catch (Exception e) {
            log.warn("Fallo la consulta en Redis a causa de: {}. Intentando consulta en PostgreSQL...", e.getMessage());
        }

        // 2. Si Redis esta vacio o fallo, consultar en PostgreSQL
        try {
            log.info("Consultando productos almacenados en PostgreSQL (tabla gestopago_productos)...");
            List<ProductoEntity> entities = productoRepository.findAll();

            if (entities != null && !entities.isEmpty()) {
                List<ProductoDTO> productos = new ArrayList<>();
                for (ProductoEntity entity : entities) {
                    ProductoDTO dto = ProductoDTO.builder()
                            .idProducto(entity.getIdProducto())
                            .producto(entity.getProducto())
                            .idServicio(entity.getIdServicio())
                            .servicio(entity.getServicio())
                            .idCatTipoServicio(entity.getIdCatTipoServicio())
                            .tipoFront(entity.getTipoFront())
                            .legend(entity.getLegend())
                            .build();
                    productos.add(dto);
                }

                log.info("Productos obtenidos correctamente desde POSTGRESQL. Total: {}", productos.size());
                return ProductoListResponse.builder()
                        .codigo(0)
                        .mensaje("Operacion realizada con exito")
                        .origen("POSTGRESQL")
                        .productos(productos)
                        .build();
            }
            log.info("PostgreSQL no contiene productos almacenados.");
        } catch (Exception e) {
            log.error("Error al consultar productos en PostgreSQL: {}", e.getMessage(), e);
        }

        // 3. Si ambas plataformas estan vacias
        log.info("No se encontraron productos almacenados ni en Redis ni en PostgreSQL.");
        return ProductoListResponse.builder()
                .codigo(1)
                .mensaje("No hay productos almacenados en Redis ni en PostgreSQL")
                .origen("NINGUNO")
                .productos(new ArrayList<>())
                .build();
    }



    /**
     * Almacena los productos de forma permanente en PostgreSQL y en la memoria cache de Redis.
     * Si Redis falla, se registra en logs pero la persistencia en PostgreSQL continua.
     */
    private void guardarProductosConFallback(List<ProductoDTO> listaProductos) {
        if (listaProductos == null || listaProductos.isEmpty()) {
            return;
        }

        // 1. Intentar guardar en Redis (Caché rápido)
        try {
            log.info("Almacenando {} productos en Redis (key='{}')...", listaProductos.size(), REDIS_PRODUCTOS_KEY);
            redisTemplate.opsForValue().set(REDIS_PRODUCTOS_KEY, listaProductos, Duration.ofHours(24));
            log.info("Productos guardados exitosamente en Redis.");

        } catch (Exception redisException) {
            log.warn("No se pudo almacenar en Redis a causa de: {}. Continuando con guardado en PostgreSQL...",
                    redisException.getMessage());
        }

        // 2. Guardar SIEMPRE en PostgreSQL (Persistencia en base de datos)
        guardarEnPostgreSQL(listaProductos);
    }

    /**
     * Guarda / actualiza la lista de productos en la base de datos PostgreSQL.
     */
    private void guardarEnPostgreSQL(List<ProductoDTO> listaProductos) {
        try {
            List<ProductoEntity> entities = new ArrayList<>();
            LocalDateTime ahora = LocalDateTime.now();

            for (ProductoDTO dto : listaProductos) {
                if (dto.getIdProducto() != null) {
                    ProductoEntity entity = ProductoEntity.builder()
                            .idProducto(dto.getIdProducto())
                            .producto(dto.getProducto() != null ? dto.getProducto() : "")
                            .idServicio(dto.getIdServicio() != null ? dto.getIdServicio() : 0)
                            .servicio(dto.getServicio() != null ? dto.getServicio() : "")
                            .idCatTipoServicio(dto.getIdCatTipoServicio())
                            .tipoFront(dto.getTipoFront())
                            .legend(dto.getLegend())
                            .fechaActualizacion(ahora)
                            .build();
                    entities.add(entity);
                }
            }

            if (!entities.isEmpty()) {
                productoRepository.saveAll(entities);
                log.info("FALLBACK EXITOSO: {} productos guardados/actualizados en PostgreSQL (tabla gestopago_productos).", entities.size());
            }

        } catch (Exception dbException) {
            log.error("Error al guardar productos en PostgreSQL durante fallback: {}", dbException.getMessage(), dbException);
        }
    }

    private String getAttrString(Element el, String attrName) {
        if (el.hasAttribute(attrName)) {
            return el.getAttribute(attrName);
        }
        if (el.hasAttribute(attrName.toLowerCase())) {
            return el.getAttribute(attrName.toLowerCase());
        }
        return null;
    }

    private Integer parseAttrInt(Element el, String attrName) {
        String val = getAttrString(el, attrName);
        if (val != null && !val.isBlank()) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String getLegendContent(Element el) {
        NodeList legendList = el.getElementsByTagName("legend");
        if (legendList.getLength() == 0) {
            legendList = el.getElementsByTagName("LEGEND");
        }
        if (legendList.getLength() > 0) {
            return legendList.item(0).getTextContent();
        }
        return null;
    }
}

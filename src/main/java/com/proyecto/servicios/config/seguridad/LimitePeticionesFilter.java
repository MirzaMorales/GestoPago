package com.proyecto.servicios.config.seguridad;

import com.proyecto.servicios.exception.ApiErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Límite de peticiones por IP (ventana fija en Redis) para frenar fuerza bruta, registros masivos y
 * saturación del servicio. Los endpoints públicos tienen límites propios más estrictos; el resto comparte uno general.
 * Si Redis no responde, no se bloquea el servicio (se registra en el log).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LimitePeticionesFilter extends OncePerRequestFilter {

    static final String MENSAJE = "Demasiadas solicitudes. Espera un momento e intenta de nuevo";
    private static final String PREFIJO = "limite:";
    private static final Pattern IP_VALIDA = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    /** {@code ruta} terminada en "/" cubre todo lo que cuelga de ella; si no, debe coincidir exacta. */
    record Regla(String nombre, String metodo, String ruta, int maximo, Duration ventana) {
        boolean aplica(HttpServletRequest r) {
            String uri = r.getRequestURI();
            boolean coincide = ruta.endsWith("/") ? uri.startsWith(ruta) : uri.equals(ruta);
            return (metodo == null || metodo.equals(r.getMethod())) && coincide;
        }
    }

    private final StringRedisTemplate redis;
    private final ApiErrorResponseWriter errores;
    private final List<Regla> reglas;
    private final Regla general;

    public LimitePeticionesFilter(StringRedisTemplate redis, ApiErrorResponseWriter errores,
                                  @Value("${app.limite.login-por-minuto:10}") int login,
                                  @Value("${app.limite.2fa-por-minuto:15}") int dosFactores,
                                  @Value("${app.limite.registro-por-hora:10}") int registro,
                                  @Value("${app.limite.recuperacion-por-hora:10}") int recuperacion,
                                  @Value("${app.limite.catalogo-por-minuto:120}") int catalogo,
                                  @Value("${app.limite.general-por-minuto:300}") int generalPorMinuto) {
        this.redis = redis;
        this.errores = errores;
        Duration minuto = Duration.ofMinutes(1);
        this.reglas = List.of(
                new Regla("login", "POST", "/auth/login", login, minuto),
                new Regla("2fa", "POST", "/auth/verificar-2fa", dosFactores, minuto),
                new Regla("registro", "POST", "/clientes", registro, Duration.ofHours(1)),
                new Regla("recuperacion", "POST", "/auth/recuperar/", recuperacion, Duration.ofHours(1)),
                new Regla("catalogo", "GET", "/catalogos/", catalogo, minuto));
        this.general = new Regla("general", null, "/", generalPorMinuto, minuto);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String ip = ipCliente(request);
        Regla regla = reglas.stream().filter(r -> r.aplica(request)).findFirst().orElse(general);

        long restante = segundosParaReintentar(regla, ip);
        if (restante > 0) {
            response.setHeader("Retry-After", String.valueOf(restante));
            errores.write(response, HttpStatus.TOO_MANY_REQUESTS, MENSAJE);
            return;
        }
        chain.doFilter(request, response);
    }

    /** 0 si la petición está dentro del límite; si no, segundos que faltan para que se libere la ventana. */
    private long segundosParaReintentar(Regla regla, String ip) {
        long ventana = regla.ventana().toSeconds();
        long bloque = System.currentTimeMillis() / 1000 / ventana;
        String clave = PREFIJO + regla.nombre() + ":" + ip + ":" + bloque;
        try {
            Long cuenta = redis.opsForValue().increment(clave);
            if (cuenta != null && cuenta == 1) {
                redis.expire(clave, regla.ventana().plusSeconds(5));
            }
            if (cuenta != null && cuenta > regla.maximo()) {
                if (cuenta == regla.maximo() + 1) {
                    log.warn("Límite '{}' alcanzado para una IP ({} solicitudes)", regla.nombre(), regla.maximo());
                }
                return Math.max(1, (bloque + 1) * ventana - System.currentTimeMillis() / 1000);
            }
        } catch (RuntimeException e) {
            log.error("No se pudo aplicar el límite de solicitudes: {}", e.getClass().getSimpleName());
        }
        return 0;
    }

    /**
     * IP real del cliente. Render atiende detrás de Cloudflare, que fija CF-Connecting-IP / True-Client-IP
     * (el cliente no puede falsificarlas); X-Forwarded-For queda como respaldo y al final la IP de la conexión.
     */
    static String ipCliente(HttpServletRequest request) {
        for (String cabecera : List.of("CF-Connecting-IP", "True-Client-IP", "X-Forwarded-For")) {
            String valor = request.getHeader(cabecera);
            if (valor != null && !valor.isBlank()) {
                String ip = valor.split(",")[0].trim();
                if (IP_VALIDA.matcher(ip).matches()) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }
}

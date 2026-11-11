package com.proyecto.servicios.config.seguridad;

import com.proyecto.servicios.exception.ApiErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rechaza cuerpos de solicitud demasiado grandes (413) antes de leerlos: ningún endpoint necesita más de unos KB,
 * y aceptar megabytes permitiría agotar memoria y CPU con pocas peticiones. Cubre también envíos sin Content-Length
 * (chunked), cortando la lectura al pasar el límite.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LimiteTamanoSolicitudFilter extends OncePerRequestFilter {

    static final String MENSAJE = "La solicitud excede el tamaño permitido";

    private final long maxBytes;
    private final ApiErrorResponseWriter errores;

    public LimiteTamanoSolicitudFilter(@Value("${app.seguridad.max-cuerpo-bytes:32768}") long maxBytes,
                                       ApiErrorResponseWriter errores) {
        this.maxBytes = maxBytes;
        this.errores = errores;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declarado = request.getContentLengthLong();
        if (declarado > maxBytes) {
            errores.write(response, HttpStatus.PAYLOAD_TOO_LARGE, MENSAJE);
            return;
        }
        chain.doFilter(declarado < 0 ? new SolicitudLimitada(request, maxBytes) : request, response);
    }

    /** Limita la lectura del cuerpo cuando no se declaró su tamaño. */
    static final class SolicitudLimitada extends HttpServletRequestWrapper {
        private final long max;
        private ServletInputStream stream;

        SolicitudLimitada(HttpServletRequest request, long max) {
            super(request);
            this.max = max;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new StreamLimitado(super.getInputStream(), max);
            }
            return stream;
        }
    }

    static final class StreamLimitado extends ServletInputStream {
        private final ServletInputStream origen;
        private final long max;
        private long leidos;

        StreamLimitado(ServletInputStream origen, long max) {
            this.origen = origen;
            this.max = max;
        }

        private void contar(long n) throws IOException {
            if (n > 0 && (leidos += n) > max) {
                throw new IOException(MENSAJE);
            }
        }

        @Override
        public int read() throws IOException {
            int b = origen.read();
            if (b >= 0) contar(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = origen.read(b, off, len);
            contar(n);
            return n;
        }

        @Override
        public boolean isFinished() {
            return origen.isFinished();
        }

        @Override
        public boolean isReady() {
            return origen.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            origen.setReadListener(listener);
        }
    }
}

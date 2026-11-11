package com.proyecto.servicios.config.seguridad;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.coyote.ActionCode;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reemplaza la página HTML de error de Tomcat (que revela el servidor y su versión) por un JSON mínimo.
 * Aplica a errores que ocurren antes de llegar a Spring, por ejemplo una URL con caracteres inválidos ({@code <script>}).
 */
public class ErroresTomcatJsonValve extends ErrorReportValve {

    public ErroresTomcatJsonValve() {
        setShowReport(false);
        setShowServerInfo(false);
    }

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        int status = response.getStatus();
        if (status < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        AtomicBoolean ioPermitido = new AtomicBoolean(true);
        response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, ioPermitido);
        if (!ioPermitido.get()) {
            return;
        }
        try {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            Writer writer = response.getReporter();
            if (writer != null) {
                writer.write("{\"status\":" + status + ",\"mensaje\":\"" + mensaje(status) + "\"}");
                response.finishResponse();
            }
        } catch (IOException | IllegalStateException ignorada) {
            // La respuesta ya no se puede escribir; no hay nada más que hacer.
        }
    }

    static String mensaje(int status) {
        return switch (status) {
            case 400 -> "La solicitud no es válida";
            case 404 -> "El recurso solicitado no existe";
            case 413 -> "La solicitud excede el tamaño permitido";
            default -> status >= 500 ? "Ocurrió un error interno al procesar la solicitud" : "La solicitud no pudo procesarse";
        };
    }
}

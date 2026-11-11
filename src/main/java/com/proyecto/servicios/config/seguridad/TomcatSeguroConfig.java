package com.proyecto.servicios.config.seguridad;

import org.apache.catalina.core.StandardHost;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/** Errores de Tomcat en JSON y sin información del servidor (ver {@link ErroresTomcatJsonValve}). */
@Component
public class TomcatSeguroConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addContextCustomizers(context -> {
            if (context.getParent() instanceof StandardHost host) {
                // Se fija antes de que arranque el host, que es cuando Tomcat instancia esta válvula.
                host.setErrorReportValveClass(ErroresTomcatJsonValve.class.getName());
            }
        });
    }
}

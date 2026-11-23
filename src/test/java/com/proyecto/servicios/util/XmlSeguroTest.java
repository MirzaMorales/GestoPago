package com.proyecto.servicios.util;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** El XML del proveedor no puede leer archivos del servidor (XXE) ni expandir entidades sin control. */
class XmlSeguroTest {

    @Test
    void leeLaRespuestaNormalDelProveedor() throws Exception {
        Document doc = XmlSeguro.parsear("<?xml version='1.0' encoding='UTF-8'?><RESPONSE><MENSAJE>"
                + "<CODIGO>01</CODIGO><TEXTO>Operacion realizada con exito</TEXTO></MENSAJE></RESPONSE>");
        assertEquals("01", doc.getElementsByTagName("CODIGO").item(0).getTextContent());
    }

    @Test
    void rechazaEntidadesExternasQueLeenArchivos() {
        String xxe = "<?xml version='1.0'?><!DOCTYPE r [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"
                + "<RESPONSE><TEXTO>&x;</TEXTO></RESPONSE>";
        assertThrows(Exception.class, () -> XmlSeguro.parsear(xxe));
    }

    @Test
    void rechazaLaExpansionMasivaDeEntidades() {
        String bomba = "<?xml version='1.0'?><!DOCTYPE r [<!ENTITY a 'aaaaaaaaaa'>"
                + "<!ENTITY b '&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;'><!ENTITY c '&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;'>]>"
                + "<RESPONSE>&c;</RESPONSE>";
        assertThrows(Exception.class, () -> XmlSeguro.parsear(bomba));
    }
}

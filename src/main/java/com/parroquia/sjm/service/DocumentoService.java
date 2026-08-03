package com.parroquia.sjm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    /**
     * Mapa de documentos disponibles.
     * La clave es el nombre público (usado en la URL) y el valor es la ruta
     * al archivo .docx dentro de src/main/resources/templates/manuales/
     */
    private static final Map<String, String> DOCUMENTOS = Map.ofEntries(
        Map.entry("guia-instalacion-windows", "manuales/Guia_Instalacion_Windows.docx"),
        Map.entry("guia-instalacion-android", "manuales/Guia_Instalacion_Android.docx"),
        Map.entry("guia-instalacion-ios", "manuales/Guia_Instalacion_iOS.docx"),
        Map.entry("manual-agenda", "manuales/Manual_Agenda_Parroquial.docx"),
        Map.entry("manual-misas", "manuales/Manual_Gestion_Misas.docx"),
        Map.entry("manual-bautizo", "manuales/Manual_Bautizo.docx"),
        Map.entry("manual-confirmacion", "manuales/Manual_Confirmacion.docx"),
        Map.entry("manual-matrimonio", "manuales/Manual_Matrimonio.docx")
    );

    /**
     * Mapa de instaladores disponibles (archivos .rar).
     * La clave es el nombre público (usado en la URL) y el valor es la ruta
     * al archivo .rar dentro de src/main/resources/templates/instaladores/
     */
    private static final Map<String, String> INSTALADORES = Map.ofEntries(
        Map.entry("instalador-windows", "instaladores/Instalador_windows.rar"),
        Map.entry("instalador-android", "instaladores/Instalador_android.rar"),
        Map.entry("instalador-ios", "instaladores/Instalador_IOS.rar")
    );

    public record DocumentoReporte(byte[] contenido, String nombreArchivo) {}

    /**
     * Obtiene el contenido de un documento .docx por su nombre público.
     * 
     * @param nombre Clave del documento (ej: "guia-instalacion-windows")
     * @return Mono con el contenido del archivo y su nombre real
     */
    public Mono<DocumentoReporte> obtenerDocumento(String nombre) {
        String ruta = DOCUMENTOS.get(nombre);
        if (ruta == null) {
            return Mono.error(new IllegalArgumentException("Documento no encontrado: " + nombre));
        }
        return leerArchivo("templates/" + ruta);
    }

    /**
     * Obtiene el contenido de un instalador .rar por su nombre público.
     * 
     * @param nombre Clave del instalador (ej: "instalador-windows")
     * @return Mono con el contenido del archivo y su nombre real
     */
    public Mono<DocumentoReporte> obtenerInstalador(String nombre) {
        String ruta = INSTALADORES.get(nombre);
        if (ruta == null) {
            return Mono.error(new IllegalArgumentException("Instalador no encontrado: " + nombre));
        }
        return leerArchivo("templates/" + ruta);
    }

    /**
     * Lee un archivo del classpath y devuelve su contenido junto con el nombre real.
     */
    private Mono<DocumentoReporte> leerArchivo(String classpathRuta) {
        return Mono.fromCallable(() -> {
            ClassPathResource res = new ClassPathResource(classpathRuta);
            try (InputStream is = res.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }

                String nombreArchivo = classpathRuta.substring(classpathRuta.lastIndexOf('/') + 1);
                return new DocumentoReporte(out.toByteArray(), nombreArchivo);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
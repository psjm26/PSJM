package com.parroquia.sjm.controller;

import com.parroquia.sjm.model.dto.BautizosDTO;
import com.parroquia.sjm.model.dto.HorarioDTO;
import com.parroquia.sjm.model.dto.MisaParticularDTO;
import com.parroquia.sjm.model.dto.MisaComunitariaDTO;
import com.parroquia.sjm.model.response.Horario.HorarioMisas;
import com.parroquia.sjm.model.response.MisaDisponibilidadResponse;
import com.parroquia.sjm.model.response.MisasConsultaResponse;
import com.parroquia.sjm.service.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/psjm")
@CrossOrigin(origins = "*")
public class ParroquiaSJMController {

    private final MisaParticularService misaParticularService;
    private final MisaComunitariaService misaComunitariaService;
    private final BautizosService bautizosService;
    private final HorarioService horarioService;
    private final UsuarioService usuarioService;
    private final DocumentoService documentoService;

    private static final String MS_WORD_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public ParroquiaSJMController(MisaParticularService misaParticularService, 
                                  MisaComunitariaService misaComunitariaService, 
                                  BautizosService bautizosService, 
                                  HorarioService horarioService, 
                                  UsuarioService usuarioService,
                                  DocumentoService documentoService) {
        this.misaParticularService = misaParticularService;
        this.misaComunitariaService = misaComunitariaService;
        this.bautizosService = bautizosService;
        this.horarioService = horarioService;
        this.usuarioService = usuarioService;
        this.documentoService = documentoService;
    }

    @GetMapping("/health")
    public Mono<String> healthCheck() {
        return Mono.just("OK");
    }

    @GetMapping("/consultar/horario")
    public Flux<HorarioMisas> consultarHorarioMisas(
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin) {
        return horarioService.obtenerHorarios(fechaInicio, fechaFin);
    }

    @PostMapping("/guardar/evento")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> guardarEvento(@RequestBody Map<String, Object> evento) {
        return horarioService.guardarEvento(evento);
    }

    @PatchMapping("/actualizar/evento")
    public Mono<Void> actualizarEvento(@RequestParam Long id, @RequestBody Map<String, Object> cambios) {
        return horarioService.actualizarEvento(id, cambios);
    }

    @PostMapping("/guardar/bautizo")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> guardarBautizo(@RequestBody BautizosDTO bautizo) {
        return bautizosService.guardarBautizo(bautizo);
    }

    @PostMapping("/agregar/intencion")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> agregarIntencion(
            @RequestParam String tipoIntencion,
            @RequestBody Map<String, Object> datos) {
        
        if ("Particular".equalsIgnoreCase(tipoIntencion)) {
            return misaParticularService.guardarMisaParticular(datos);
        } else if ("Comunitaria".equalsIgnoreCase(tipoIntencion)) {
            return misaComunitariaService.guardarMisaComunitaria(datos);
        } else {
            return Mono.error(new IllegalArgumentException("Tipo de intención no válido: " + tipoIntencion));
        }
    }

    @GetMapping("/consultar/misa-disponible")
    public Mono<MisaDisponibilidadResponse> consultarMisaDisponible(
            @RequestParam String fecha,
            @RequestParam String hora) {
        
        Mono<Boolean> particularExiste = misaParticularService.existeMisaParticular(fecha, hora);
        Mono<Boolean> comunitariaExiste = misaComunitariaService.existeMisaComunitaria(fecha, hora);

        return Mono.zip(particularExiste, comunitariaExiste)
                .map(tuple -> {
                    boolean existe = tuple.getT1() || tuple.getT2();
                    return new MisaDisponibilidadResponse(existe ? "S" : "N");
                });
    }

    /**
     * Consulta unificada de misas particulares y comunitarias por rango de fechas.
     */
    @GetMapping("/consultar/misas")
    public Mono<MisasConsultaResponse> consultarMisas(
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin) {
        
        Mono<java.util.List<MisaParticularDTO>> particularList = 
            misaParticularService.consultarMisasParticularesRango(fechaInicio, fechaFin).collectList();
        
        Mono<java.util.List<MisaComunitariaDTO>> comunitariaList = 
            misaComunitariaService.consultarMisasComunitariasRango(fechaInicio, fechaFin).collectList();

        return Mono.zip(particularList, comunitariaList)
                .map(tuple -> new MisasConsultaResponse(tuple.getT1(), tuple.getT2()));
    }

    @GetMapping("/exportar/constancia/bautizo")
    public Mono<ResponseEntity<byte[]>> exportarBautizo(@RequestParam Long id) {
        return bautizosService.exportarCertificadoBautizo(id)
                .map(reporte -> {
                    String nombreArchivo = "Constancia_" + reporte.nombrePersona().replace(" ", "_") + ".docx";
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nombreArchivo)
                            .contentType(MediaType.parseMediaType(MS_WORD_TYPE))
                            .body(reporte.contenido());
                });
    }

    @GetMapping("/exportar/misaparticular")
    public Mono<ResponseEntity<byte[]>> exportarMisasParticulares(@RequestParam String fecha) {
        return misaParticularService.exportarMisasParticulares(fecha)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Misas_Particulares_" + fecha + ".docx")
                        .contentType(MediaType.parseMediaType(MS_WORD_TYPE))
                        .body(bytes));
    }

    @GetMapping("/exportar/misacomunitaria")
    public Mono<ResponseEntity<byte[]>> exportarMisaComunitaria(
            @RequestParam String fecha,
            @RequestParam String hora) {
        
        String horaCorta = hora.contains(":") ? hora.split(":")[0] : hora;

        return misaComunitariaService.exportarMisaComunitaria(fecha, hora)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Misa_Comunitaria_" + fecha + "_" + horaCorta + ".docx")
                        .contentType(MediaType.parseMediaType(MS_WORD_TYPE))
                        .body(bytes));
    }

    /**
     * Sirve un documento .docx (guía de instalación, manual de usuario, etc.)
     * almacenado en resources/templates/manuales/.
     * El frontend lo convierte a HTML con Mammoth.js para vista previa,
     * y puede descargarlo como archivo .docx original.
     */
    @GetMapping("/consultar/documento")
    public Mono<ResponseEntity<byte[]>> consultarDocumento(@RequestParam String nombre) {
        return documentoService.obtenerDocumento(nombre)
                .map(reporte -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + reporte.nombreArchivo())
                        .contentType(MediaType.parseMediaType(MS_WORD_TYPE))
                        .body(reporte.contenido()));
    }

    /**
     * Sirve un instalador .rar (Windows, Android, iOS) almacenado en
     * resources/templates/instaladores/. Se descarga como attachment.
     */
    @GetMapping("/descargar/instalador")
    public Mono<ResponseEntity<byte[]>> descargarInstalador(@RequestParam String nombre) {
        return documentoService.obtenerInstalador(nombre)
                .map(reporte -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + reporte.nombreArchivo())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(reporte.contenido()));
    }
}

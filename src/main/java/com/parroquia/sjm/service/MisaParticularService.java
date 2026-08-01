package com.parroquia.sjm.service;

import com.parroquia.sjm.model.dto.MisaParticularDTO;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MisaParticularService {

    private final WebClient supabaseWebClient;
    private static final Logger log = LoggerFactory.getLogger(MisaParticularService.class);
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    public MisaParticularService(WebClient supabaseWebClient) {
        this.supabaseWebClient = supabaseWebClient;
    }

    public Flux<MisaParticularDTO> consultarMisasParticulares(String fecha, String hora) {
        return supabaseWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/v1/misas_particulares")
                        .queryParam("fecha_misa", "eq." + fecha)
                        .queryParam("hora_misa", "eq." + hora)
                        .queryParam("order", "created_at.asc")
                        .build())
                .retrieve()
                .bodyToFlux(MisaParticularDTO.class)
                .map(MisaParticularDTO::withTipoMisa); // Inyectamos el tipo
    }

    public Mono<Boolean> existeMisaParticular(String fecha, String hora) {
        return supabaseWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/v1/misas_particulares")
                        .queryParam("fecha_misa", "eq." + fecha)
                        .queryParam("hora_misa", "eq." + hora)
                        .queryParam("select", "id")
                        .queryParam("limit", "1")
                        .build())
                .retrieve()
                .bodyToFlux(Map.class)
                .hasElements();
    }

    public Mono<Void> guardarMisaParticular(Map<String, Object> datos) {
        return supabaseWebClient.post()
                .uri("/rest/v1/misas_particulares")
                .bodyValue(datos)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                    response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Error de Supabase al guardar misa particular: {}", errorBody);
                                return Mono.error(new RuntimeException("Error al guardar en Supabase"));
                            })
                )
                .bodyToMono(Void.class);
    }

    public Flux<MisaParticularDTO> consultarMisasParticularesRango(String fechaInicio, String fechaFin) {
        return supabaseWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/v1/misas_particulares")
                        .queryParam("fecha_misa", "gte." + fechaInicio)
                        .queryParam("fecha_misa", "lte." + fechaFin)
                        .queryParam("order", "fecha_misa.asc,hora_misa.asc")
                        .build())
                .retrieve()
                .bodyToFlux(MisaParticularDTO.class)
                .map(MisaParticularDTO::withTipoMisa); // Inyectamos el tipo
    }

    public Mono<byte[]> exportarMisasParticulares(String fecha) {
        return supabaseWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/v1/misas_particulares")
                        .queryParam("fecha_misa", "eq." + fecha)
                        .queryParam("order", "hora_misa.asc")
                        .build())
                .retrieve()
                .bodyToFlux(MisaParticularDTO.class)
                .collectList()
                .flatMap(this::generarWordDesdeLista);
    }

    private Mono<byte[]> generarWordDesdeLista(List<MisaParticularDTO> misas) {
        return Mono.fromCallable(() -> {
            if (misas.isEmpty()) {
                return crearDocumentoVacio("No hay misas para esta fecha");
            }

            try (XWPFDocument document = new XWPFDocument()) {
                LocalDate fechaObj = misas.get(0).fechaMisa();
                String diaSemana = fechaObj.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
                diaSemana = diaSemana.substring(0, 1).toUpperCase() + diaSemana.substring(1);
                String encabezadoTexto = String.format("%s %02d", diaSemana, fechaObj.getDayOfMonth());

                XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT);
                XWPFParagraph headerPara = header.createParagraph();
                headerPara.setAlignment(ParagraphAlignment.CENTER);
                headerPara.setBorderBottom(Borders.DOUBLE);
                
                XWPFRun headerRun = headerPara.createRun();
                headerRun.setText(encabezadoTexto);
                headerRun.setBold(true);
                headerRun.setFontSize(20);
                headerRun.setFontFamily("Arial");

                for (MisaParticularDTO misa : misas) {
                    XWPFTable table = document.createTable(1, 1);
                    table.removeBorders();
                    
                    CTTblWidth width = table.getCTTbl().addNewTblPr().addNewTblW();
                    width.setType(STTblWidth.DXA);
                    width.setW(BigInteger.valueOf(9072));

                    XWPFTableCell cell = table.getRow(0).getCell(0);
                    table.getRow(0).setCantSplitRow(true);

                    XWPFParagraph p1 = cell.getParagraphs().get(0);
                    p1.setSpacingBefore(100);
                    XWPFRun r1 = p1.createRun();
                    r1.setText("Hora: " + misa.horaMisa().format(timeFormatter));
                    r1.setBold(true);
                    r1.setFontSize(13);
                    r1.setFontFamily("Arial");
                    r1.addBreak();

                    XWPFRun r2 = p1.createRun();
                    r2.setText(misa.intencion());
                    r2.setBold(true);
                    r2.setFontSize(12);
                    r2.setFontFamily("Arial");

                    XWPFParagraph p2 = cell.addParagraph();
                    XWPFRun rOfrece = p2.createRun();
                    rOfrece.setText("Ofrece: " + misa.ofrece());
                    rOfrece.setItalic(true);
                    rOfrece.setFontSize(11);
                    rOfrece.setFontFamily("Arial");

                    XWPFParagraph p3 = cell.addParagraph();
                    p3.setBorderBottom(Borders.SINGLE);
                    p3.setSpacingAfter(200);

                    document.createParagraph();
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                document.write(out);
                return out.toByteArray();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private byte[] crearDocumentoVacio(String mensaje) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p = document.createParagraph();
            p.createRun().setText(mensaje);
            document.write(out);
            return out.toByteArray();
        }
    }
}
package com.parroquia.sjm.service;

import com.parroquia.sjm.model.dto.MisaComunitariaDTO;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MisaComunitariaService {

    private final WebClient supabaseWebClient;
    private static final Logger log = LoggerFactory.getLogger(MisaComunitariaService.class);
    private final List<String> CATEGORIAS_ORDENADAS = List.of("ACCIÓN DE GRACIAS", "SALUD", "DIFUNTOS");

    public MisaComunitariaService(WebClient supabaseWebClient) {
        this.supabaseWebClient = supabaseWebClient;
    }

    public Flux<MisaComunitariaDTO> consultarMisasComunitarias(String fecha, String hora) {
        return supabaseWebClient.get()
                .uri(uriBuilder -> {
                    var u = uriBuilder
                            .path("/rest/v1/misas_comunitarias")
                            .queryParam("fecha_misa", "eq." + fecha)
                            .queryParam("hora_misa", "eq." + hora)
                            .build();
                    log.debug("Supabase request URI (consultarMisasComunitarias): {}", u);
                    return u;
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Supabase GET misas_comunitarias error (fecha={}, hora={}): {} - {}",
                                            fecha, hora, response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Error al consultar Supabase: " + errorBody));
                                })
                )
                .bodyToFlux(MisaComunitariaDTO.class)
                .collectList()
                .flatMapMany(this::transformarMisasComunitarias);
    }

    public Flux<MisaComunitariaDTO> consultarMisasComunitariasRango(String fechaInicio, String fechaFin) {
        return supabaseWebClient.get()
                .uri(uriBuilder -> {
                    var u = uriBuilder
                            .path("/rest/v1/misas_comunitarias")
                            .queryParam("fecha_misa", "gte." + fechaInicio)
                            .queryParam("fecha_misa", "lte." + fechaFin)
                            .queryParam("order", "fecha_misa.asc,hora_misa.asc")
                            .build();
                    log.debug("Supabase request URI (consultarMisasComunitariasRango): {}", u);
                    return u;
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Supabase GET misas_comunitarias rango error (inicio={}, fin={}): {} - {}",
                                            fechaInicio, fechaFin, response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Error al consultar Supabase: " + errorBody));
                                })
                )
                .bodyToFlux(MisaComunitariaDTO.class)
                .collectList()
                .flatMapMany(this::transformarMisasComunitarias);
    }

    private Flux<MisaComunitariaDTO> transformarMisasComunitarias(List<MisaComunitariaDTO> misas) {
        Map<String, List<MisaComunitariaDTO>> agrupadas = misas.stream()
                .collect(Collectors.groupingBy(m -> m.fechaMisa() + "|" + m.horaMisa()));

        return Flux.fromIterable(agrupadas.values())
                .map(grupo -> {
                    MisaComunitariaDTO primera = grupo.get(0);

                    String intencionSalud = grupo.stream()
                            .map(MisaComunitariaDTO::intencionSalud)
                            .filter(Objects::nonNull)
                            .filter(s -> !s.isBlank())
                            .collect(Collectors.joining("\n"));

                    String intencionAccionGracias = grupo.stream()
                            .map(MisaComunitariaDTO::intencionAccionGracias)
                            .filter(Objects::nonNull)
                            .filter(s -> !s.isBlank())
                            .collect(Collectors.joining("\n"));

                    String intencionDifuntos = grupo.stream()
                            .map(MisaComunitariaDTO::intencionDifuntos)
                            .filter(Objects::nonNull)
                            .filter(s -> !s.isBlank())
                            .collect(Collectors.joining("\n"));

                    return new MisaComunitariaDTO(
                            primera.id(), primera.fechaMisa(), primera.horaMisa(),
                            intencionSalud.isEmpty() ? null : intencionSalud,
                            intencionAccionGracias.isEmpty() ? null : intencionAccionGracias,
                            intencionDifuntos.isEmpty() ? null : intencionDifuntos,
                            primera.ofrenda(), primera.pagado(), primera.anotaciones(),
                            primera.celebrante(), primera.idMisa(), primera.createdAt(), primera.updatedAt()
                    );
                });
    }

    public Mono<Boolean> existeMisaComunitaria(String fecha, String hora) {
        return supabaseWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/v1/misas_comunitarias")
                        .queryParam("fecha_misa", "eq." + fecha)
                        .queryParam("hora_misa", "eq." + hora)
                        .queryParam("select", "id").queryParam("limit", "1").build())
                .retrieve().bodyToFlux(Map.class).hasElements();
    }

    public Mono<Void> guardarMisaComunitaria(Map<String, Object> datos) {
        return supabaseWebClient.post()
                .uri("/rest/v1/misas_comunitarias")
                .bodyValue(datos).retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                    response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Error de Supabase al guardar misa comunitaria: {}", errorBody);
                        return Mono.error(new RuntimeException("Error al guardar en Supabase"));
                    }))
                .bodyToMono(Void.class);
    }

    public Mono<byte[]> exportarMisaComunitaria(String fecha, String hora) {
        return supabaseWebClient.get()
                .uri(uriBuilder -> {
                    var u = uriBuilder.path("/rest/v1/misas_comunitarias")
                            .queryParam("fecha_misa", "eq." + fecha)
                            .queryParam("hora_misa", "eq." + hora)
                            .queryParam("order", "fecha_misa.asc,hora_misa.asc").build();
                    return u;
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                    response.bodyToMono(String.class).flatMap(errorBody -> {
                        log.error("Supabase GET misas_comunitarias export error: {} - {}", response.statusCode(), errorBody);
                        return Mono.error(new RuntimeException("Error al consultar Supabase: " + errorBody));
                    }))
                .bodyToFlux(MisaComunitariaDTO.class)
                .collectList()
                .flatMap(misas -> generarWordComunitario(misas, fecha, hora));
    }

    private Mono<byte[]> generarWordComunitario(List<MisaComunitariaDTO> misas, String fechaStr, String horaStr) {
        return Mono.fromCallable(() -> {
            try (XWPFDocument document = new XWPFDocument()) {
                CTSectPr sectPr = document.getDocument().getBody().addNewSectPr();
                CTPageMar pageMar = sectPr.addNewPgMar();
                pageMar.setTop(BigInteger.valueOf(460));
                pageMar.setBottom(BigInteger.valueOf(460));
                pageMar.setLeft(BigInteger.valueOf(720));
                pageMar.setRight(BigInteger.valueOf(720));

                LocalDate date = LocalDate.parse(fechaStr);
                LocalTime time = LocalTime.parse(horaStr);
                String diaSemana = date.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es", "ES")).toUpperCase();
                String tituloTexto = String.format("%s %02d - %s", diaSemana, date.getDayOfMonth(),
                        time.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)).toUpperCase());

                XWPFParagraph titlePara = document.createParagraph();
                titlePara.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun titleRun = titlePara.createRun();
                titleRun.setText(tituloTexto);
                titleRun.setBold(true);
                titleRun.setFontSize(18);
                titleRun.setFontFamily("Arial");
                titlePara.setSpacingAfter(400);

                for (String cat : CATEGORIAS_ORDENADAS) {
                    XWPFParagraph catPara = document.createParagraph();
                    XWPFRun catRun = catPara.createRun();
                    catRun.setText(cat);
                    catRun.setBold(true);
                    catRun.setFontSize(14);
                    catRun.setFontFamily("Arial");

                    String prefijo;
                    if ("SALUD".equalsIgnoreCase(cat)) {
                        prefijo = "- ";
                    } else if ("DIFUNTOS".equalsIgnoreCase(cat)) {
                        prefijo = "+ ";
                    } else {
                        prefijo = "• ";
                    }

                    List<String> textos = new ArrayList<>();
                    for (MisaComunitariaDTO m : misas) {
                        String texto = null;
                        if ("SALUD".equalsIgnoreCase(cat)) texto = m.intencionSalud();
                        else if ("ACCIÓN DE GRACIAS".equalsIgnoreCase(cat)) texto = m.intencionAccionGracias();
                        else if ("DIFUNTOS".equalsIgnoreCase(cat)) texto = m.intencionDifuntos();
                        if (texto != null && !texto.isBlank()) textos.add(texto);
                    }

                    if (textos.isEmpty()) {
                        for (int i = 0; i < 4; i++) document.createParagraph();
                    } else {
                        for (String texto : textos) {
                            for (String line : texto.split("\\r?\\n")) {
                                XWPFParagraph p = document.createParagraph();
                                p.setSpacingAfter(0); // Sin espacio después del párrafo (pegado)
                                p.setSpacingBefore(0);
                                XWPFRun r = p.createRun();
                                r.setFontSize(13);
                                r.setFontFamily("Arial");
                                r.setText(prefijo + line);
                            }
                        }
                        document.createParagraph();
                    }
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                document.write(out);
                return out.toByteArray();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
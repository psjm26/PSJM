package com.parroquia.sjm.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

/**
 * Record DTO para Misas Comunitarias.
 * Los campos de intención se mantienen como String desde Supabase,
 * el frontend los separa por saltos de línea para visualización por labels.
 */
public record MisaComunitariaDTO(
    Long id,
    @JsonProperty("fecha_misa")
    LocalDate fechaMisa,
    @JsonProperty("hora_misa")
    LocalTime horaMisa,
    @JsonProperty("intencionSalud")
    String intencionSalud,
    @JsonProperty("intencionAccionGracias")
    String intencionAccionGracias,
    @JsonProperty("intencionDifuntos")
    String intencionDifuntos,
    BigDecimal ofrenda,
    Boolean pagado,
    String anotaciones,
    String celebrante,
    @JsonProperty("idMisa")
    String idMisa,
    @JsonProperty("created_at")
    OffsetDateTime createdAt,
    @JsonProperty("updated_at")
    OffsetDateTime updatedAt
) {
}
package com.parroquia.sjm.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

/**
 * Record DTO para Misas Particulares.
 */
public record MisaParticularDTO(
    Long id,
    @JsonProperty("fecha_misa")
    LocalDate fechaMisa,
    @JsonProperty("hora_misa")
    LocalTime horaMisa,
    String intencion,
    String ofrece,
    BigDecimal ofrenda,
    Boolean pagado,
    String anotaciones,
    String celebrante,
    @JsonProperty("idMisa")
    String idMisa,
    @JsonProperty("created_at")
    OffsetDateTime createdAt,
    @JsonProperty("updated_at")
    OffsetDateTime updatedAt,
    @JsonProperty("tipoMisa")
    String tipoMisa
) {
    // Constructor compacto que preserva el tipoMisa real de la BD
    public MisaParticularDTO withTipoMisa() {
        String tipo = (this.tipoMisa != null && !this.tipoMisa.isBlank()) ? this.tipoMisa : "Misa Particular";
        return new MisaParticularDTO(id, fechaMisa, horaMisa, intencion, ofrece, ofrenda, pagado, anotaciones, celebrante, idMisa, createdAt, updatedAt, tipo);
    }
}
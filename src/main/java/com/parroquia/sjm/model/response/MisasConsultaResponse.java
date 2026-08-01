package com.parroquia.sjm.model.response;

import com.parroquia.sjm.model.dto.MisaComunitariaDTO;
import com.parroquia.sjm.model.dto.MisaParticularDTO;
import java.util.List;

public record MisasConsultaResponse(
    List<MisaParticularDTO> particular,
    List<MisaComunitariaDTO> comunitaria
) {
}
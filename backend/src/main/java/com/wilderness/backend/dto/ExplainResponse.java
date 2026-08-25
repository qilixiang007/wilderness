package com.wilderness.backend.dto;

import java.util.List;

public record ExplainResponse(String answer, List<AiSource> sources) {
}

package com.wilderness.backend.dto;

import java.util.List;

public record ChatResponse(String answer, List<AiSource> sources) {
}

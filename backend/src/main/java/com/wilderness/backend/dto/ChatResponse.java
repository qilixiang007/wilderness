package com.wilderness.backend.dto;

import java.util.List;

/** retrievalDegraded=true 表示知识库检索失败，answer 是大模型不依赖检索资料直接给出的。 */
public record ChatResponse(String answer, List<AiSource> sources, boolean retrievalDegraded) {
}

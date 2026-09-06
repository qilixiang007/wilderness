package com.wilderness.backend.dto;

import java.util.List;

/**
 * 对比中单个天体的结果。error 非空即该项已降级(讲解生成失败/超时/无资料),
 * 此时 zhName/enName/answer/sources 均为 null,前端据 error 展示友好提示。
 */
public record CompareItemResult(String slug, String zhName, String enName, String answer, List<AiSource> sources, String error) {

    public static CompareItemResult ok(String slug, String zhName, String enName, String answer, List<AiSource> sources) {
        return new CompareItemResult(slug, zhName, enName, answer, sources, null);
    }

    public static CompareItemResult failed(String slug, String reason) {
        return new CompareItemResult(slug, null, null, null, null, reason);
    }
}

package com.wilderness.backend.dto;

import java.util.List;

/**
 * 对比中单个天体的结果。error 非空即该项已降级(讲解生成失败/超时/无资料),
 * 此时 zhName/enName/answer/sources 均为 null,前端据 error 展示友好提示。
 * retrievalDegraded=true 表示讲解正常生成了，但知识库检索失败，answer 未经知识库核实——
 * 跟 error 互斥（error 非空时 retrievalDegraded 恒为 false）。
 */
public record CompareItemResult(String slug, String zhName, String enName, String answer, List<AiSource> sources,
		String error, boolean retrievalDegraded) {

	public static CompareItemResult ok(String slug, String zhName, String enName, String answer,
			List<AiSource> sources, boolean retrievalDegraded) {
		return new CompareItemResult(slug, zhName, enName, answer, sources, null, retrievalDegraded);
	}

	public static CompareItemResult failed(String slug, String reason) {
		return new CompareItemResult(slug, null, null, null, null, reason, false);
	}
}

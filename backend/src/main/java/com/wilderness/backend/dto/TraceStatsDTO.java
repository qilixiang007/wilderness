package com.wilderness.backend.dto;

import java.util.List;

/**
 * 管理员统计概览（ES 聚合）。时间均为 epoch 毫秒；耗时单位毫秒，无数据时为 null。
 * timeline 只包含有数据的时间桶，空桶由前端补零。
 */
public record TraceStatsDTO(
		long from,
		long to,
		String interval,
		Summary summary,
		List<TimeBucket> timeline,
		List<NameStat> byName,
		List<ModelStat> byModel) {

	public record Summary(
			long traces,
			long errors,
			Double p50Ms,
			Double p95Ms,
			Double ttftP50Ms,
			Double ttftP95Ms,
			long totalTokens) {
	}

	public record TimeBucket(
			long time,
			long traces,
			long errors,
			Double p50Ms,
			Double p95Ms,
			long tokens) {
	}

	public record NameStat(
			String name,
			long traces,
			long errors,
			Double p50Ms,
			Double p95Ms) {
	}

	public record ModelStat(
			String model,
			long calls,
			long promptTokens,
			long completionTokens,
			long totalTokens) {
	}

	public static TraceStatsDTO empty(long from, long to, String interval) {
		return new TraceStatsDTO(from, to, interval, new Summary(0, 0, null, null, null, null, 0),
				List.of(), List.of(), List.of());
	}
}

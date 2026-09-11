package com.wilderness.backend.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.ArrayPercentilesItem;
import co.elastic.clients.elasticsearch._types.aggregations.Percentiles;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.wilderness.backend.ai.trace.store.TraceEsIndexManager;
import com.wilderness.backend.dto.TraceStatsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员统计概览，全部基于 ES 聚合（MySQL 不适合做分位数与时间分桶）。
 *
 * 三次查询：
 * 1. 根 span：调用量、错误数、耗时 P50/P95，按时间分桶、按操作名拆分；
 * 2. 带 token 的 span（llm / embedding）：token 总量、按时间分桶、按模型拆分；
 * 3. 带首 token 时间的根 span：流式 TTFT 的 P50/P95。
 *
 * 空集合防护：ES 对空集合的 percentiles 返回 null，而 Java 客户端的字符串反序列化器不接受 JSON null，
 * 所以做分位数之前先 count 确认有数据，时间桶设 min_doc_count=1 不产生空桶。
 */
@Service
public class TraceStatsService {

	private static final Logger log = LoggerFactory.getLogger(TraceStatsService.class);
	private static final Duration DEFAULT_RANGE = Duration.ofHours(24);
	/** 与数据保留期一致，更早的数据已被清理。 */
	private static final Duration MAX_RANGE = Duration.ofDays(7);
	private static final Duration HOURLY_LIMIT = Duration.ofHours(48);
	private static final int TOP_N = 20;

	private final ElasticsearchClient es;
	private final TraceEsIndexManager indexManager;

	public TraceStatsService(ElasticsearchClient es, TraceEsIndexManager indexManager) {
		this.es = es;
		this.indexManager = indexManager;
	}

	public TraceStatsDTO stats(Long fromMs, Long toMs) {
		long to = toMs != null ? toMs : System.currentTimeMillis();
		long from = fromMs != null ? fromMs : to - DEFAULT_RANGE.toMillis();
		from = Math.max(from, to - MAX_RANGE.toMillis());
		if (from >= to) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "时间范围不合法");
		}
		String interval = to - from <= HOURLY_LIMIT.toMillis() ? "1h" : "1d";
		try {
			return doStats(from, to, interval);
		} catch (ResponseStatusException e) {
			throw e;
		} catch (Exception e) {
			log.warn("trace 统计查询失败: {}", e.getMessage());
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "统计服务暂不可用（ES 异常）");
		}
	}

	private TraceStatsDTO doStats(long from, long to, String interval) throws Exception {
		Query roots = spansQuery(from, to, true, "duration_ms");
		if (count(roots) == 0) {
			return TraceStatsDTO.empty(from, to, interval);
		}

		SearchResponse<Void> rootResp = search(roots, Map.of(
				"errors", errorsAgg(),
				"latency", latencyAgg("duration_ms"),
				"timeline", Aggregation.of(a -> a
						.dateHistogram(h -> h.field("start_time").fixedInterval(fi -> fi.time(interval)).minDocCount(1))
						.aggregations("errors", errorsAgg())
						.aggregations("latency", latencyAgg("duration_ms"))),
				"byName", Aggregation.of(a -> a
						.terms(t -> t.field("trace_name").size(TOP_N))
						.aggregations("errors", errorsAgg())
						.aggregations("latency", latencyAgg("duration_ms")))));

		// sum 聚合在空集合上返回 0，不需要 count 防护
		SearchResponse<Void> tokenResp = search(spansQuery(from, to, false, "total_tokens"), Map.of(
				"total", sumAgg("total_tokens"),
				"timeline", Aggregation.of(a -> a
						.dateHistogram(h -> h.field("start_time").fixedInterval(fi -> fi.time(interval)).minDocCount(1))
						.aggregations("tokens", sumAgg("total_tokens"))),
				"byModel", Aggregation.of(a -> a
						.terms(t -> t.field("model_name").size(TOP_N))
						.aggregations("prompt", sumAgg("prompt_tokens"))
						.aggregations("completion", sumAgg("completion_tokens"))
						.aggregations("total", sumAgg("total_tokens")))));

		Double ttftP50 = null;
		Double ttftP95 = null;
		Query streamRoots = spansQuery(from, to, true, "first_token_ms");
		if (count(streamRoots) > 0) {
			Aggregate ttft = search(streamRoots, Map.of("ttft", latencyAgg("first_token_ms")))
					.aggregations().get("ttft");
			ttftP50 = percentile(ttft, 50.0);
			ttftP95 = percentile(ttft, 95.0);
		}

		Map<String, Aggregate> r = rootResp.aggregations();
		Map<String, Aggregate> t = tokenResp.aggregations();

		Map<Long, Long> tokensByTime = new LinkedHashMap<>();
		t.get("timeline").dateHistogram().buckets().array()
				.forEach(b -> tokensByTime.put(b.key(), (long) b.aggregations().get("tokens").sum().value()));

		List<TraceStatsDTO.TimeBucket> timeline = r.get("timeline").dateHistogram().buckets().array().stream()
				.map(b -> new TraceStatsDTO.TimeBucket(
						b.key(),
						b.docCount(),
						b.aggregations().get("errors").filter().docCount(),
						percentile(b.aggregations().get("latency"), 50.0),
						percentile(b.aggregations().get("latency"), 95.0),
						tokensByTime.getOrDefault(b.key(), 0L)))
				.toList();

		List<TraceStatsDTO.NameStat> byName = r.get("byName").sterms().buckets().array().stream()
				.map(b -> new TraceStatsDTO.NameStat(
						b.key().stringValue(),
						b.docCount(),
						b.aggregations().get("errors").filter().docCount(),
						percentile(b.aggregations().get("latency"), 50.0),
						percentile(b.aggregations().get("latency"), 95.0)))
				.toList();

		List<TraceStatsDTO.ModelStat> byModel = t.get("byModel").sterms().buckets().array().stream()
				.map(b -> new TraceStatsDTO.ModelStat(
						b.key().stringValue(),
						b.docCount(),
						(long) b.aggregations().get("prompt").sum().value(),
						(long) b.aggregations().get("completion").sum().value(),
						(long) b.aggregations().get("total").sum().value()))
				.toList();

		TraceStatsDTO.Summary summary = new TraceStatsDTO.Summary(
				rootResp.hits().total() == null ? count(roots) : rootResp.hits().total().value(),
				r.get("errors").filter().docCount(),
				percentile(r.get("latency"), 50.0),
				percentile(r.get("latency"), 95.0),
				ttftP50,
				ttftP95,
				(long) t.get("total").sum().value());
		return new TraceStatsDTO(from, to, interval, summary, timeline, byName, byModel);
	}

	/** 时间范围内的 span；rootOnly 只要根 span；requiredField 要求该字段存在（保证聚合对象非空）。 */
	private Query spansQuery(long from, long to, boolean rootOnly, String requiredField) {
		return Query.of(q -> q.bool(b -> {
			// 必须用 date range + 字符串毫秒值：number range 会把 long 转成 double，序列化成 1.7E12 这种科学计数法，
			// epoch_millis 格式解析失败，整个查询 all shards failed（已用 curl 复现）
			b.filter(f -> f.range(rg -> rg.date(d -> d
					.field("start_time")
					.gte(String.valueOf(from))
					.lt(String.valueOf(to))
					.format("epoch_millis"))));
			b.filter(f -> f.exists(e -> e.field(requiredField)));
			if (rootOnly) {
				b.filter(f -> f.term(tm -> tm.field("is_root").value(true)));
			}
			return b;
		}));
	}

	private long count(Query query) throws Exception {
		return es.count(c -> c
				.index(indexManager.indexPattern())
				.ignoreUnavailable(true)
				.allowNoIndices(true)
				.query(query)).count();
	}

	private SearchResponse<Void> search(Query query, Map<String, Aggregation> aggregations) throws Exception {
		return es.search(s -> s
				.index(indexManager.indexPattern())
				.ignoreUnavailable(true)
				.allowNoIndices(true)
				.size(0)
				.trackTotalHits(th -> th.enabled(true))
				.query(query)
				.aggregations(aggregations), Void.class);
	}

	private static Aggregation errorsAgg() {
		return Aggregation.of(a -> a.filter(f -> f.term(t -> t.field("status").value("error"))));
	}

	private static Aggregation latencyAgg(String field) {
		return Aggregation.of(a -> a.percentiles(p -> p.field(field).percents(50.0, 95.0)));
	}

	private static Aggregation sumAgg(String field) {
		return Aggregation.of(a -> a.sum(s -> s.field(field)));
	}

	/** 兼容 keyed（默认，Map&lt;"50.0", 值&gt;）与数组两种返回形态；取不到或非数值返回 null。 */
	private static Double percentile(Aggregate aggregate, double percent) {
		Percentiles values = aggregate.tdigestPercentiles().values();
		if (values.isKeyed()) {
			return parse(values.keyed().get(String.valueOf(percent)));
		}
		for (ArrayPercentilesItem item : values.array()) {
			if (item.key() == percent) {
				return Double.isNaN(item.value()) ? null : item.value();
			}
		}
		return null;
	}

	private static Double parse(String value) {
		if (value == null) {
			return null;
		}
		try {
			double d = Double.parseDouble(value);
			return Double.isNaN(d) ? null : d;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}

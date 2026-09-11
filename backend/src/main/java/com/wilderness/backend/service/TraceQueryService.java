package com.wilderness.backend.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.wilderness.backend.ai.trace.store.TraceEsIndexManager;
import com.wilderness.backend.auth.AuthService;
import com.wilderness.backend.domain.AiSpan;
import com.wilderness.backend.domain.AiTrace;
import com.wilderness.backend.domain.User;
import com.wilderness.backend.dto.SpanDTO;
import com.wilderness.backend.dto.TraceDetailDTO;
import com.wilderness.backend.dto.TracePageDTO;
import com.wilderness.backend.dto.TraceSummaryDTO;
import com.wilderness.backend.repository.AiSpanRepository;
import com.wilderness.backend.repository.AiTraceRepository;
import com.wilderness.backend.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * trace 查询。数据隔离在这里统一做：管理员不过滤（可按 userId 筛），普通用户强制只看自己的。
 * - 列表、详情走 MySQL（权威存储），ES 挂了照常可用；
 * - 关键词走 ES 全文检索拿候选 traceId，再回 MySQL 按其余条件分页，ES 不可用时明确报 503。
 */
@Service
public class TraceQueryService {

	private static final Logger log = LoggerFactory.getLogger(TraceQueryService.class);
	private static final int MAX_PAGE_SIZE = 100;
	/** 关键词命中的候选 trace 上限（按 ES terms 聚合取），超出时只在这部分里分页并告知前端。 */
	private static final int KEYWORD_CANDIDATES = 1000;

	private final AiTraceRepository traceRepository;
	private final AiSpanRepository spanRepository;
	private final UserRepository userRepository;
	private final AuthService authService;
	private final ElasticsearchClient es;
	private final TraceEsIndexManager indexManager;
	private final ObjectMapper objectMapper;

	public TraceQueryService(AiTraceRepository traceRepository, AiSpanRepository spanRepository,
			UserRepository userRepository, AuthService authService, ElasticsearchClient es,
			TraceEsIndexManager indexManager, ObjectMapper objectMapper) {
		this.traceRepository = traceRepository;
		this.spanRepository = spanRepository;
		this.userRepository = userRepository;
		this.authService = authService;
		this.es = es;
		this.indexManager = indexManager;
		this.objectMapper = objectMapper;
	}

	/** 列表查询条件；时间为 epoch 毫秒，均可为空。filterUserId 仅管理员生效。 */
	public record Criteria(String name, String status, Long from, Long to, String keyword, Long filterUserId) {
	}

	public TracePageDTO list(Long currentUserId, Criteria criteria, int page, int size) {
		boolean admin = authService.isAdminUser(currentUserId);
		Long scopeUserId = admin ? criteria.filterUserId() : currentUserId;

		List<String> candidates = null;
		boolean truncated = false;
		if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
			candidates = searchCandidates(criteria, scopeUserId);
			truncated = candidates.size() >= KEYWORD_CANDIDATES;
			if (candidates.isEmpty()) {
				return new TracePageDTO(List.of(), 0, 0, Math.max(page, 0), size, false);
			}
		}

		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "startTime"));
		Page<AiTrace> result = traceRepository.findAll(spec(criteria, scopeUserId, candidates), pageable);

		Map<Long, String> emails = admin ? emailsOf(result.getContent()) : Map.of();
		List<TraceSummaryDTO> items = result.getContent().stream()
				.map(t -> toSummary(t, emails.get(t.getUserId())))
				.toList();
		return new TracePageDTO(items, result.getTotalElements(), result.getTotalPages(), result.getNumber(),
				result.getSize(), truncated);
	}

	/** 详情：非本人且非管理员一律 404，不暴露 trace 是否存在。 */
	public TraceDetailDTO detail(Long currentUserId, String traceId) {
		AiTrace trace = traceRepository.findById(traceId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "调用链路不存在"));
		boolean admin = authService.isAdminUser(currentUserId);
		if (!admin && !Objects.equals(trace.getUserId(), currentUserId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "调用链路不存在");
		}
		String email = admin ? emailsOf(List.of(trace)).get(trace.getUserId()) : null;
		List<SpanDTO> spans = spanRepository.findByTraceIdOrderByDottedOrderAsc(traceId).stream()
				.map(this::toSpan)
				.toList();
		return new TraceDetailDTO(toSummary(trace, email), spans);
	}

	private Specification<AiTrace> spec(Criteria c, Long scopeUserId, List<String> candidates) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (scopeUserId != null) {
				predicates.add(cb.equal(root.get("userId"), scopeUserId));
			}
			if (c.name() != null && !c.name().isBlank()) {
				predicates.add(cb.equal(root.get("name"), c.name()));
			}
			if (c.status() != null && !c.status().isBlank()) {
				predicates.add(cb.equal(root.get("status"), c.status()));
			}
			if (c.from() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), Instant.ofEpochMilli(c.from())));
			}
			if (c.to() != null) {
				predicates.add(cb.lessThan(root.get("startTime"), Instant.ofEpochMilli(c.to())));
			}
			if (candidates != null) {
				predicates.add(root.get("traceId").in(candidates));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	/**
	 * 在 span 的输入/输出/错误里做短语匹配（中文按字切词，短语匹配才等价于「包含这段文字」），
	 * 按 trace_id 聚合出候选集合。普通用户额外按 user_id 过滤，匿名 trace 没有 user_id 自然查不到。
	 */
	private List<String> searchCandidates(Criteria c, Long scopeUserId) {
		try {
			SearchResponse<Void> response = es.search(s -> s
					.index(indexManager.indexPattern())
					.ignoreUnavailable(true)
					.allowNoIndices(true)
					.size(0)
					.query(q -> q.bool(b -> keywordQuery(b, c, scopeUserId)))
					.aggregations("traces", a -> a.terms(t -> t.field("trace_id").size(KEYWORD_CANDIDATES))),
					Void.class);
			return response.aggregations().get("traces").sterms().buckets().array().stream()
					.map(bucket -> bucket.key().stringValue())
					.toList();
		} catch (Exception e) {
			log.warn("trace 关键词检索失败: {}", e.getMessage());
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "搜索服务暂不可用，请去掉关键词后重试");
		}
	}

	private BoolQuery.Builder keywordQuery(BoolQuery.Builder b, Criteria c, Long scopeUserId) {
		b.must(m -> m.multiMatch(mm -> mm
				.query(c.keyword().trim())
				.type(TextQueryType.Phrase)
				.fields("inputs", "outputs", "error")));
		if (scopeUserId != null) {
			b.filter(f -> f.term(t -> t.field("user_id").value(String.valueOf(scopeUserId))));
		}
		if (c.from() != null || c.to() != null) {
			// date range + 字符串毫秒值（number range 会序列化成科学计数法，epoch_millis 解析失败）
			b.filter(f -> f.range(r -> r.date(d -> {
				d.field("start_time").format("epoch_millis");
				if (c.from() != null) {
					d.gte(String.valueOf(c.from()));
				}
				if (c.to() != null) {
					d.lt(String.valueOf(c.to()));
				}
				return d;
			})));
		}
		return b;
	}

	private Map<Long, String> emailsOf(List<AiTrace> traces) {
		List<Long> ids = traces.stream().map(AiTrace::getUserId).filter(Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return userRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
	}

	private TraceSummaryDTO toSummary(AiTrace t, String email) {
		return new TraceSummaryDTO(t.getTraceId(), t.getName(), t.getStatus(), t.getStartTime(), t.getDurationMs(),
				t.getSpanCount(), t.getPromptTokens(), t.getCompletionTokens(), t.getTotalTokens(),
				t.getFirstTokenMs(), t.getErrorMessage(), t.getUserId(), email);
	}

	private SpanDTO toSpan(AiSpan s) {
		int depth = (int) s.getDottedOrder().chars().filter(ch -> ch == '.').count();
		return new SpanDTO(s.getSpanId(), s.getParentSpanId(), depth, s.getName(), s.getRunType(), s.getStatus(),
				s.getStartTime(), s.getEndTime(), s.getDurationMs(), s.getModelName(), s.getPromptTokens(),
				s.getCompletionTokens(), s.getTotalTokens(), s.getFirstTokenMs(),
				parse(s.getInputsJson()), parse(s.getOutputsJson()), s.getError());
	}

	/** 库里存的是 JSON 字符串，解析成对象返回；万一不是合法 JSON 就原样作为文本。 */
	private JsonNode parse(String json) {
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readTree(json);
		} catch (Exception e) {
			return TextNode.valueOf(json);
		}
	}
}

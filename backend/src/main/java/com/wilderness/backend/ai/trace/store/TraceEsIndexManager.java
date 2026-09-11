package com.wilderness.backend.ai.trace.store;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * span 分析索引：按天建索引（wilderness-trace-spans-yyyy.MM.dd），保留期到了整个索引删掉，
 * 比 delete_by_query 便宜得多。映射通过索引模板下发，按天新建的索引自动套用。
 *
 * 模板必须先于第一次写入就位：否则 ES 会按动态映射把 keyword 字段建成 text，后续聚合统计失效。
 * 所以写入前会检查 templateReady，未就位就先补建，建不了就不写（留给补偿任务）。
 */
@Component
public class TraceEsIndexManager {

    static final DateTimeFormatter INDEX_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC);

    private final ElasticsearchClient es;
    private final String prefix;
    private volatile boolean templateReady;

    public TraceEsIndexManager(ElasticsearchClient es,
                               @Value("${wilderness.trace.store.es-index-prefix:wilderness-trace-spans}") String prefix) {
        this.es = es;
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    /** 所有按天索引的通配名，查询与统计用。 */
    public String indexPattern() {
        return prefix + "-*";
    }

    /** 按 trace 开始时间（UTC）决定落在哪个日索引，同一棵 trace 的 span 始终在同一个索引里。 */
    public String indexFor(long traceStartMs) {
        return prefix + "-" + INDEX_DATE.format(Instant.ofEpochMilli(traceStartMs));
    }

    /** 从索引名解析日期；不是本前缀的索引返回 null。 */
    public LocalDate dateOf(String indexName) {
        if (!indexName.startsWith(prefix + "-")) {
            return null;
        }
        try {
            return LocalDate.parse(indexName.substring(prefix.length() + 1), DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isTemplateReady() {
        return templateReady;
    }

    /** 下发（覆盖）索引模板，幂等。 */
    public void ensureTemplate() throws Exception {
        es.indices().putIndexTemplate(t -> t
                .name(prefix)
                .indexPatterns(indexPattern())
                .template(tm -> tm
                        // 单节点部署：副本数为 0，否则索引永远是 yellow
                        .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                        .mappings(m -> m
                                .properties("trace_id", p -> p.keyword(k -> k))
                                .properties("span_id", p -> p.keyword(k -> k))
                                .properties("parent_span_id", p -> p.keyword(k -> k))
                                .properties("is_root", p -> p.boolean_(b -> b))
                                .properties("user_id", p -> p.keyword(k -> k))
                                .properties("trace_name", p -> p.keyword(k -> k))
                                .properties("name", p -> p.keyword(k -> k))
                                .properties("run_type", p -> p.keyword(k -> k))
                                .properties("status", p -> p.keyword(k -> k))
                                .properties("model_name", p -> p.keyword(k -> k))
                                .properties("start_time", p -> p.date(d -> d.format("epoch_millis")))
                                .properties("duration_ms", p -> p.long_(l -> l))
                                .properties("first_token_ms", p -> p.long_(l -> l))
                                .properties("prompt_tokens", p -> p.integer(i -> i))
                                .properties("completion_tokens", p -> p.integer(i -> i))
                                .properties("total_tokens", p -> p.integer(i -> i))
                                .properties("inputs", p -> p.text(t2 -> t2))
                                .properties("outputs", p -> p.text(t2 -> t2))
                                .properties("error", p -> p.text(t2 -> t2)))));
        templateReady = true;
    }
}

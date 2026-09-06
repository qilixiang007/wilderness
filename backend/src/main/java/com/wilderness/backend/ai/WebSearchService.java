package com.wilderness.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 联网检索服务:聚合全部已注册的检索源,去重、按配置截断摘要。
 * 供 RAG 在用户开启"联网检索"时,补充本地知识库之外的权威资料。
 */
@Service
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);

    private final List<WebSearchSource> sources;
    private final int maxResults;
    private final int maxCharacters;

    public WebSearchService(List<WebSearchSource> sources,
                            @Value("${wilderness.ai.web.max-results}") int maxResults,
                            @Value("${wilderness.ai.web.max-characters}") int maxCharacters) {
        this.sources = sources;
        this.maxResults = maxResults;
        this.maxCharacters = maxCharacters;
    }

    /**
     * 对 query 联网检索,返回去重、截断后的结果。
     * 联网检索是本地知识库之外的"锦上添花",单个检索源超时/不可达(如必应被墙、网络抖动)
     * 不应该拖垮整个问答请求,因此这里按源隔离异常,失败的源直接跳过。
     */
    public List<WebResult> search(String query) {
        List<WebResult> all = new ArrayList<>();
        for (WebSearchSource source : sources) {
            try {
                all.addAll(source.search(query, maxResults));
            } catch (Exception e) {
                log.warn("联网检索源 [{}] 失败,跳过: {}", source.name(), e.toString());
            }
        }
        return all.stream()
                .filter(r -> r.snippet() != null && !r.snippet().isBlank())
                .distinct()
                .limit(maxResults)
                .map(r -> r.snippet().length() > maxCharacters
                        ? new WebResult(r.title(), r.url(), r.snippet().substring(0, maxCharacters))
                        : r)
                .toList();
    }
}

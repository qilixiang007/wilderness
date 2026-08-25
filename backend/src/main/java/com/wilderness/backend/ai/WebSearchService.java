package com.wilderness.backend.ai;

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

    /** 对 query 联网检索,返回去重、截断后的结果。 */
    public List<WebResult> search(String query) throws Exception {
        List<WebResult> all = new ArrayList<>();
        for (WebSearchSource source : sources) {
            all.addAll(source.search(query, maxResults));
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

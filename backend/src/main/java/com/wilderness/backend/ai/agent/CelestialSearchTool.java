package com.wilderness.backend.ai.agent;

import com.wilderness.backend.ai.HybridContentRetriever;
import com.wilderness.backend.dto.AgentStep;
import com.wilderness.backend.dto.AiSource;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天体生成 Agent 的检索工具：调用站内混合检索器查询真实天体资料，作为生成虚拟天体的科学参考锚点。
 *
 * 设计要点（面试可讲）：
 * - 请求级实例：每个生成请求 new 一个本工具，实例字段持有本次 Agent 的执行轨迹（steps/sources），
 *   天然线程隔离，无需 ThreadLocal 跨线程传递；
 * - 降级：检索异常不打断生成，返回降级文本并记录「检索异常」步骤，让 LLM 基于常识生成并标注虚构；
 * - 去重：同一天体多次命中时只保留一次来源。
 */
public class CelestialSearchTool {

    /** 返回给 LLM 的资料每段截断长度，控制 token 用量。 */
    private static final int EXCERPT_LEN = 160;

    private final HybridContentRetriever retriever;

    /** Agent 执行轨迹（LLM 调用工具时依次追加）。 */
    private final List<AgentStep> steps = new ArrayList<>();
    /** slug → zhName 去重，用于「命中哪些真实天体」展示。 */
    private final Map<String, String> hitBySlug = new LinkedHashMap<>();
    /** 供前端跳转真实天体详情页的来源列表。 */
    private final List<AiSource> sources = new ArrayList<>();

    public CelestialSearchTool(HybridContentRetriever retriever) {
        this.retriever = retriever;
    }

    /**
     * 服务端预检索（不依赖 LLM 调用工具）：
     * 直接把用户完整描述作为检索词查询真实天体资料，结果注入用户消息作参考锚点。
     * 保证即使模型不调用工具或调用失败，参考来源依然存在。
     */
    public String searchReference(String query) {
        if (query == null || query.isBlank()) {
            return "未提供检索关键词。";
        }
        return search(query);
    }

    @Tool("在站内真实天体知识库中检索指定类型、特征相关的真实天体资料（基本参数与形态描述），用于生成虚拟天体时作为科学参考锚点。")
    public String searchCelestial(@P("天体类型，如：恒星、行星、卫星、星系、星云、彗星") String type,
                                  @P("想借鉴的特征或参数，如：红色、有行星环、质量巨大、表面温度低") String feature) {
        String query = ((type == null ? "" : type) + " " + (feature == null ? "" : feature)).trim();
        // langchain4j 的 Query 不允许空文本，空检索词时直接降级，避免异常
        if (query.isEmpty()) {
            steps.add(new AgentStep("参考锚点", "未提供检索关键词"));
            return "未提供有效的检索关键词，请基于常识生成并标注虚构设定。";
        }
        return search(query);
    }

    /** 核心检索逻辑：混合检索公共语料，记录步骤与来源，返回给 LLM 的资料文本。 */
    private String search(String query) {
        steps.add(new AgentStep("知识库检索", "检索词：" + query));
        try {
            // userId=null → 只检公共语料（站内真实天体），不涉及用户上传文件
            List<Content> contents = retriever.retrieve(Query.from(query), null);
            if (contents.isEmpty()) {
                steps.add(new AgentStep("参考锚点", "未命中真实天体资料，将基于常识生成并标注虚构"));
                return "知识库未检索到相关真实天体资料，请基于常识生成并标注为虚构设定。";
            }
            StringBuilder sb = new StringBuilder();
            for (Content content : contents) {
                TextSegment seg = content.textSegment();
                String zhName = seg.metadata().getString("zh_name");
                String slug = seg.metadata().getString("slug");
                if (zhName != null && slug != null && !hitBySlug.containsKey(slug)) {
                    hitBySlug.put(slug, zhName);
                    sources.add(new AiSource(zhName, slug, seg.metadata().getString("type"), abbreviate(seg.text()),
                            intOrNull(seg.metadata().getString("chunk_index"))));
                }
                sb.append('【').append(zhName == null ? slug : zhName).append("】\n")
                        .append(abbreviate(seg.text())).append("\n\n");
            }
            steps.add(new AgentStep("参考锚点", "命中真实天体：" + String.join("、", hitBySlug.values())));
            return sb.toString();
        } catch (Exception e) {
            steps.add(new AgentStep("检索异常", e.getMessage()));
            return "检索失败：" + e.getMessage() + "，请基于常识生成并注明虚构设定。";
        }
    }

    public List<AgentStep> steps() {
        return List.copyOf(steps);
    }

    public List<AiSource> sources() {
        return List.copyOf(sources);
    }

    private String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > EXCERPT_LEN ? text.substring(0, EXCERPT_LEN) + "…" : text;
    }

    /** ES 的 chunk_index 经 Metadata 转一圈会变成字符串,这里转回数字;取不到就返回 null。 */
    private Integer intOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

package com.wilderness.backend.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.ai.HybridContentRetriever;
import com.wilderness.backend.ai.image.ImageGenerator;
import com.wilderness.backend.ai.langsmith.LangSmithTracer;
import com.wilderness.backend.ai.langsmith.RunRef;
import com.wilderness.backend.domain.AgentConfig;
import com.wilderness.backend.dto.AgentStep;
import com.wilderness.backend.dto.GenerationContent;
import com.wilderness.backend.dto.GenerationResult;
import com.wilderness.backend.service.AgentConfigService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 天体生成 Agent 门面：按请求编排一次生成，内置 Agent 与用户自定义 Agent 共用一条编排路径。
 *
 * 关键设计（面试可讲）：
 * - 统一动态 prompt 路径：内置与自定义都走 ChatRequest.builder()（不暴露工具），
 *   json_object 强制输出 + {@link #parseGenerationContent} 手工解析。之所以不用 AiServices 工具调用——
 *   qwen 兼容端点会虚构不存在的工具名，langchain4j 默认 THROW_EXCEPTION 直接降级（实测 100% 失败）；
 *   改为「服务端预检索注入 reference + 无工具提示词」，彻底消除幻觉工具名这一类故障。
 * - 服务端预检索：{@link CelestialSearchTool#searchReference} 直接以描述检索知识库并注入 prompt，
 *   来源列表与执行步骤由此兜底，Agent 的核心价值不依赖模型自觉调用工具。
 * - 工具开关：knowledgeSearchEnabled=false 时不注入站内参考（步骤注明「未启用知识检索」）；
 *   imageGenEnabled=false 时跳过文生图。内置 Agent 保持两开关均开启。
 * - 工具开关：knowledgeSearchEnabled=false 时不注入站内参考（步骤注明「未启用知识检索」）；
 *   imageGenEnabled=false 时跳过文生图。内置 Agent 保持两开关均开启。
 * - 双通道图片：文生图为可选升级（ObjectProvider<ImageGenerator> 未配置则 null），失败降级 imageUrl=null，
 *   前端回退 SVG 程序化渲染；
 * - 全程接入 LangSmith 追踪（start/finish/fail），失败同样记录。
 */
@Service
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class CelestialAgentService {

    private static final Logger log = LoggerFactory.getLogger(CelestialAgentService.class);

    private final ChatModel agentChatModel;
    private final HybridContentRetriever retriever;
    private final LangSmithTracer tracer;
    private final ObjectProvider<ImageGenerator> imageGenerators;
    private final AgentConfigService agentConfigService;
    private final ObjectMapper objectMapper;

    public CelestialAgentService(@Qualifier("agentChatModel") ChatModel agentChatModel,
                                 HybridContentRetriever retriever,
                                 LangSmithTracer tracer,
                                 ObjectProvider<ImageGenerator> imageGenerators,
                                 AgentConfigService agentConfigService,
                                 ObjectMapper objectMapper) {
        this.agentChatModel = agentChatModel;
        this.retriever = retriever;
        this.tracer = tracer;
        this.imageGenerators = imageGenerators;
        this.agentConfigService = agentConfigService;
        this.objectMapper = objectMapper;
    }

    /** 内置 Agent 生成（agent=null）：走 AiServices 接口路径，行为与重构前一致。 */
    public GenerationResult generate(String description) {
        return doGenerate(null, description);
    }

    /** 使用某个自定义智能体生成：读取其配置（人设 + 工具开关），越权由 AgentConfigService 抛 404。 */
    public GenerationResult generateWithAgent(Long userId, Long agentId, String description) {
        AgentConfig agent = agentConfigService.getOwned(userId, agentId);
        return doGenerate(agent, description);
    }

    private GenerationResult doGenerate(AgentConfig agent, String description) {
        boolean custom = agent != null;
        RunRef run = tracer.start(
                custom ? "agent.generate_celestial_custom" : "agent.generate_celestial",
                "agent",
                Map.of("description", description, "agent", custom ? agent.getName() : "builtin"));
        try {
            CelestialSearchTool tool = new CelestialSearchTool(retriever);

            // 服务端预检索：直接把用户描述作为检索词查真实天体资料并注入 prompt。
            // 即使模型不调用工具，参考锚点与来源列表也已就绪（Agent 的核心价值不依赖模型自觉）。
            // 自定义智能体关闭知识检索时跳过检索，注入提示文本。
            String reference;
            if (custom && !agent.isKnowledgeSearchEnabled()) {
                reference = "（自定义智能体未启用知识库检索，未注入站内参考资料；请基于人设与常识创作，虚构设定请在文中注明。）";
            } else {
                reference = tool.searchReference(description);
            }

            // 内置与自定义统一走动态 prompt（见 generateViaDynamicPrompt）
            GenerationContent content = generateViaDynamicPrompt(agent, description, reference);

            // 可选升级通道：文生图（未配置、自定义关闭、或生成失败，均仅降级为 SVG，不阻塞主流程）
            String imageUrl = null;
            ImageGenerator imageGenerator = imageGenerators.getIfAvailable();
            if ((!custom || agent.isImageGenEnabled()) && imageGenerator != null) {
                try {
                    imageUrl = imageGenerator.generate(buildImagePrompt(content));
                } catch (Exception e) {
                    // 文生图失败：记录但不打断，前端走 SVG
                    tool.steps().add(new AgentStep("图像生成", "文生图失败，已回退程序化渲染：" + e.getMessage()));
                }
            }

            List<AgentStep> steps = tool.steps();
            if (steps.isEmpty()) {
                // 模型未调用检索工具 / 自定义路径无工具时的兜底说明（接口照常返回，不阻断用户）
                steps = List.of(custom
                        ? new AgentStep("生成", agent.isKnowledgeSearchEnabled()
                                ? "自定义智能体已按人设与预检索参考生成"
                                : "自定义智能体未启用知识库检索，基于人设与描述生成")
                        : new AgentStep("生成", "模型未调用检索工具，直接基于描述生成"));
            }

            GenerationResult result = new GenerationResult(
                    content.name(),
                    content.type(),
                    content.parameters(),
                    content.introduction(),
                    content.render(),
                    imageUrl,
                    tool.sources(),
                    steps);

            tracer.finish(run, Map.of(
                    "name", result.name(),
                    "sourceCount", result.sources().size(),
                    "imageGenerated", result.imageUrl() != null));
            return result;
        } catch (Exception e) {
            tracer.fail(run, e);
            // 模型侧异常（如输出解析失败）：降级为可展示结果，避免 500 打断用户。
            // 结果里带异常说明步骤，前端照常渲染（视觉走 SVG、来源为空）。
            log.warn("天体生成异常，已降级返回", e);
            return degradedResult(e);
        }
    }

    /**
     * 统一生成路径：内置（agent=null）与自定义（agent≠null）共用动态 ChatRequest，
     * json_object 强制 + 手工 JSON 解析。不向模型暴露任何工具——qwen 兼容端点在工具调用下会虚构
     * 不存在的工具名（langchain4j 默认 THROW_EXCEPTION → 降级），故检索完全依赖服务端预检索注入的 reference，
     * 来源/步骤由预检索兜底（见 doGenerate）。
     */
    private GenerationContent generateViaDynamicPrompt(AgentConfig agent, String description, String reference) {
        String system = CelestialAgentPrompts.BASE_SYSTEM;
        if (agent != null) {
            system += "\n\n=== 自定义智能体设定（叠加在人设之上，须遵守，但不改变输出 JSON 契约）===\n"
                    + agent.getSystemPrompt();
        }
        String user = CelestialAgentPrompts.USER_TEMPLATE
                .replace("{{description}}", description == null ? "" : description)
                .replace("{{reference}}", reference == null ? "" : reference);
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from(system), UserMessage.from(user))
                .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).build())
                .build();
        String text = agentChatModel.chat(request).aiMessage().text();
        return parseGenerationContent(text);
    }

    /**
     * 容忍模型任意输出 → 结构化 GenerationContent：
     * 剥 ```json 围栏 → 平衡大括号抽取 → Jackson 反序列化 → 校验。
     * 解析失败抛异常，由外层统一走 degradedResult（与内置路径共用降级契约，前端渲染不变）。
     */
    private GenerationContent parseGenerationContent(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("模型未返回内容");
        }
        String json = text.trim();
        // 去掉可能的 ```json ... ``` 围栏
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                json = json.substring(firstNewline + 1, lastFence).trim();
            }
        }
        try {
            return validate(objectMapper.readValue(json, GenerationContent.class));
        } catch (Exception first) {
            // 模型可能输出说明文字后接 JSON，抽取第一个平衡大括号再试
            int start = json.indexOf('{');
            while (start >= 0) {
                int depth = 0;
                for (int i = start; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            String candidate = json.substring(start, i + 1);
                            try {
                                return validate(objectMapper.readValue(candidate, GenerationContent.class));
                            } catch (Exception ignored) {
                                break;
                            }
                        }
                    }
                }
                start = json.indexOf('{', start + 1);
            }
            throw new IllegalArgumentException("模型输出无法解析为结构化结果", first);
        }
    }

    /** 校验：name 缺失则抛给外层走降级；type/parameters/render 缺省由前端宽容渲染。 */
    private GenerationContent validate(GenerationContent c) {
        if (c == null || c.name() == null || c.name().isBlank()) {
            throw new IllegalArgumentException("模型输出缺少 name");
        }
        return c;
    }

    /** 降级结果：LLM 异常时返回一个可展示的占位，不让接口 500。 */
    private GenerationResult degradedResult(Exception e) {
        return new GenerationResult(
                "未命名天体",
                "恒星",
                Map.of("说明", "本次生成未能完成，请稍后重试"),
                "生成过程中遇到异常，未能产出完整内容。请稍后重试。\n\n> 技术细节（面试可展示容错设计）："
                        + e.getClass().getSimpleName(),
                null,
                null,
                List.of(),
                List.of(new AgentStep("生成", "模型异常已降级返回：" + e.getMessage())));
    }

    /** 由结构化结果拼文生图 prompt：让图像模型画出与参数一致的天体。 */
    private String buildImagePrompt(GenerationContent content) {
        StringBuilder sb = new StringBuilder("天文艺术概念图，");
        sb.append(content.type() == null ? "" : content.type())
                .append('「').append(content.name()).append('」');
        if (content.parameters() != null) {
            content.parameters().forEach((k, v) -> sb.append('，').append(k).append(v));
        }
        sb.append("，深空星空背景，高细节，无文字");
        return sb.toString();
    }
}

package com.wilderness.backend.ai.agent;

import com.wilderness.backend.ai.HybridContentRetriever;
import com.wilderness.backend.ai.image.ImageGenerator;
import com.wilderness.backend.ai.langsmith.LangSmithTracer;
import com.wilderness.backend.ai.langsmith.RunRef;
import com.wilderness.backend.dto.AgentStep;
import com.wilderness.backend.dto.GenerationContent;
import com.wilderness.backend.dto.GenerationResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 天体生成 Agent 门面：按请求编排一次生成。
 *
 * 关键设计（面试可讲）：
 * - 按请求构建 AiServices：工具实例（持有 trace）随请求创建，无共享可变状态、天然线程隔离；
 * - 双通道图片：文生图为可选升级（ObjectProvider<ImageGenerator> 未配置则 null），
 *   未配置/生成失败一律降级 imageUrl=null，前端回退 SVG 程序化渲染；
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

    public CelestialAgentService(@Qualifier("agentChatModel") ChatModel agentChatModel,
                                 HybridContentRetriever retriever,
                                 LangSmithTracer tracer,
                                 ObjectProvider<ImageGenerator> imageGenerators) {
        this.agentChatModel = agentChatModel;
        this.retriever = retriever;
        this.tracer = tracer;
        this.imageGenerators = imageGenerators;
    }

    public GenerationResult generate(String description) {
        RunRef run = tracer.start("agent.generate_celestial", "agent", Map.of("description", description));
        try {
            CelestialSearchTool tool = new CelestialSearchTool(retriever);
            CelestialAgent agent = AiServices.builder(CelestialAgent.class)
                    .chatModel(agentChatModel)
                    .tools(tool)
                    // 尽力强制模型使用工具（qwen 兼容端点的 tool_choice 传参依赖 langchain4j 映射，
                    // 实测可能不生效，故以服务端预检索为可靠兜底）
                    .chatRequestTransformer(req -> req.toBuilder().toolChoice(ToolChoice.REQUIRED).build())
                    .build();

            // 服务端预检索：直接把用户描述作为检索词查真实天体资料并注入 prompt。
            // 即使模型不调用工具，参考锚点与来源列表也已就绪（Agent 的核心价值不依赖模型自觉）。
            String reference = tool.searchReference(description);
            GenerationContent content = agent.generate(description, reference);

            // 可选升级通道：文生图（未配置或失败仅降级为 SVG，不阻塞主流程）
            String imageUrl = null;
            ImageGenerator imageGenerator = imageGenerators.getIfAvailable();
            if (imageGenerator != null) {
                try {
                    imageUrl = imageGenerator.generate(buildImagePrompt(content));
                } catch (Exception e) {
                    // 文生图失败：记录但不打断，前端走 SVG
                    tool.steps().add(new AgentStep("图像生成", "文生图失败，已回退程序化渲染：" + e.getMessage()));
                }
            }

            List<AgentStep> steps = tool.steps();
            if (steps.isEmpty()) {
                // 模型未调用检索工具时的兜底说明（接口照常返回，不阻断用户）
                steps = List.of(new AgentStep("生成", "模型未调用检索工具，直接基于描述生成"));
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
            // 模型侧异常（如仍偶发的幻觉工具名、输出解析失败）：降级为可展示结果，避免 500 打断用户。
            // 结果里带异常说明步骤，前端照常渲染（视觉走 SVG、来源为空）。
            log.warn("天体生成异常，已降级返回", e);
            return degradedResult(e);
        }
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

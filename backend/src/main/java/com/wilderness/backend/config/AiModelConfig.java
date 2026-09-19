package com.wilderness.backend.config;

import com.wilderness.backend.ai.AiAssistant;
import com.wilderness.backend.ai.trace.TracingChatModelListener;
import com.wilderness.backend.ai.trace.TracingEmbeddingModelListener;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * LLM 模型 Bean。
 * 使用阿里云百炼 DashScope 的 OpenAI 兼容端点,
 * 仅当配置了 DASHSCOPE_API_KEY 时才创建(未配置则跳过,不影响应用启动)。
 */
@Configuration
public class AiModelConfig {

    @Bean
    @ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
    public ChatModel chatModel(
            @Value("${wilderness.ai.dashscope.base-url}") String baseUrl,
            @Value("${wilderness.ai.dashscope.api-key}") String apiKey,
            @Value("${wilderness.ai.dashscope.chat-model}") String modelName,
            TracingChatModelListener tracingListener) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                // 多天体对比编排层用 orTimeout(60s) 兜底,底层超时须更短,
                // 否则线程池的线程会被卡住不释放,而不是真正按时归还。
                .timeout(Duration.ofSeconds(45))
                .listeners(List.of(tracingListener))
                .build();
    }

    @Bean
    @ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
    public EmbeddingModel embeddingModel(
            @Value("${wilderness.ai.dashscope.base-url}") String baseUrl,
            @Value("${wilderness.ai.dashscope.api-key}") String apiKey,
            @Value("${wilderness.ai.dashscope.embedding-model}") String modelName,
            @Value("${wilderness.ai.dashscope.embedding-dimensions}") Integer dimensions,
            TracingEmbeddingModelListener tracingListener) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .dimensions(dimensions)
                .listeners(List.of(tracingListener))
                .build();
    }

    @Bean
    @ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
    public StreamingChatModel streamingChatModel(
            @Value("${wilderness.ai.dashscope.base-url}") String baseUrl,
            @Value("${wilderness.ai.dashscope.api-key}") String apiKey,
            @Value("${wilderness.ai.dashscope.chat-model}") String modelName,
            TracingChatModelListener tracingListener) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                // 与上面阻塞模型一致的 45s。注意这一层只盖得住「上游连响应头都不给」:
                // 底层 JdkHttpClient 把它映射到 JDK HttpRequest.timeout(),而流式走的是
                // sendAsync + ofInputStream,响应头一到 future 就完成了,后续吐字不再受约束。
                // 「吐了一半卡住」只能靠 AiController 的 SseEmitter 超时兜。
                .timeout(Duration.ofSeconds(45))
                .listeners(List.of(tracingListener))
                .build();
    }

    /**
     * 天体生成 Agent 专用模型：创作型任务用更高 temperature(0.7) 增强表现力；
     * 与问答共用一个模型名，配置独立 bean，避免污染 chatModel(0.2) 的严谨性。
     */
    @Bean
    @ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
    public ChatModel agentChatModel(
            @Value("${wilderness.ai.dashscope.base-url}") String baseUrl,
            @Value("${wilderness.ai.dashscope.api-key}") String apiKey,
            @Value("${wilderness.ai.dashscope.chat-model}") String modelName,
            TracingChatModelListener tracingListener) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.7)
                // 与上面问答用的 chatModel 一致:同步阻塞调用必须有底层超时兜底,
                // 否则 DashScope 响应异常慢时,这次生成请求会没有上限地一直挂着。
                .timeout(Duration.ofSeconds(45))
                .listeners(List.of(tracingListener))
                .build();
    }

    /** AI 助手编排接口实现,由 langchain4j AiServices 动态生成。 */
    @Bean
    @ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
    public AiAssistant aiAssistant(@Qualifier("chatModel") ChatModel chatModel,
                                   StreamingChatModel streamingChatModel) {
        return AiServices.builder(AiAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}

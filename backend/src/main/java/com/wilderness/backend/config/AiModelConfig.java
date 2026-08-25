package com.wilderness.backend.config;

import com.wilderness.backend.ai.AiAssistant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            @Value("${wilderness.ai.dashscope.chat-model}") String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                .build();
    }

    @Bean
    @ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
    public EmbeddingModel embeddingModel(
            @Value("${wilderness.ai.dashscope.base-url}") String baseUrl,
            @Value("${wilderness.ai.dashscope.api-key}") String apiKey,
            @Value("${wilderness.ai.dashscope.embedding-model}") String modelName,
            @Value("${wilderness.ai.dashscope.embedding-dimensions}") Integer dimensions) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .dimensions(dimensions)
                .build();
    }

    @Bean
    @ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
    public StreamingChatModel streamingChatModel(
            @Value("${wilderness.ai.dashscope.base-url}") String baseUrl,
            @Value("${wilderness.ai.dashscope.api-key}") String apiKey,
            @Value("${wilderness.ai.dashscope.chat-model}") String modelName) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                .build();
    }

    /** AI 助手编排接口实现,由 langchain4j AiServices 动态生成。 */
    @Bean
    @ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
    public AiAssistant aiAssistant(ChatModel chatModel, StreamingChatModel streamingChatModel) {
        return AiServices.builder(AiAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}

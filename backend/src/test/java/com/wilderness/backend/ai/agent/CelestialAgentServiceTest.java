package com.wilderness.backend.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.ai.HybridContentRetriever;
import com.wilderness.backend.ai.image.ImageGenerator;
import com.wilderness.backend.ai.trace.Tracer;
import com.wilderness.backend.dto.GenerationResult;
import com.wilderness.backend.service.AgentConfigService;
import com.wilderness.backend.service.CelestialGenerationHistoryService;
import com.wilderness.backend.service.GeneratedImageStorageService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回归用例：文生图失败时，CelestialSearchTool.steps() 返回的是不可变副本，
 * 若误对它 add() 会抛 UnsupportedOperationException，被外层 catch 误判为「模型异常」，
 * 把本已生成成功的结果整体降级为 degradedResult()（success=false，名字变成「未命名天体」）。
 * 正确行为：文生图失败只应让 imageUrl 为空、success 仍为 true，前端据此回退 SVG 渲染。
 */
class CelestialAgentServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void 文生图失败时仍应返回成功结果且图片为空() throws Exception {
        String json = "{\"name\":\"蓝焰星\",\"type\":\"恒星\","
                + "\"parameters\":{\"质量\":\"3倍太阳\"},\"introduction\":\"一颗虚构的恒星。\"}";
        ChatModel agentChatModel = mock(ChatModel.class);
        when(agentChatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(json)).build());

        HybridContentRetriever retriever = mock(HybridContentRetriever.class);
        when(retriever.retrieve(any(Query.class), isNull())).thenReturn(List.of());

        ImageGenerator failingImageGenerator = mock(ImageGenerator.class);
        when(failingImageGenerator.generate(any())).thenThrow(new RuntimeException("模拟文生图失败"));
        ObjectProvider<ImageGenerator> imageGenerators = mock(ObjectProvider.class);
        when(imageGenerators.getIfAvailable()).thenReturn(failingImageGenerator);

        CelestialAgentService service = new CelestialAgentService(
                agentChatModel,
                retriever,
                Tracer.noop(),
                imageGenerators,
                mock(AgentConfigService.class),
                new ObjectMapper(),
                mock(CelestialGenerationHistoryService.class),
                mock(GeneratedImageStorageService.class));

        GenerationResult result = service.generate(null, "一颗散发蓝色光芒的恒星");

        assertTrue(result.success(), "文生图失败不应让整次生成被误判为模型异常而整体降级");
        assertNull(result.imageUrl(), "文生图失败应回退为空 imageUrl，前端据此走 SVG 渲染");
    }
}

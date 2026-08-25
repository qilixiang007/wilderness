package com.wilderness.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.ai.RagService;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.ChatRequest;
import com.wilderness.backend.dto.ChatResponse;
import com.wilderness.backend.dto.ExplainResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/ai")
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class AiController {

    private final RagService ragService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AiController(RagService ragService, ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.objectMapper = objectMapper;
    }

    /** AI 问答:混合检索(+可选联网)+ 生成,返回回答与引文来源。 */
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) throws Exception {
        return ApiResponse.ok(ragService.chat(request.question(), request.webSearchEnabled()));
    }

    /** AI 流式问答(SSE):先推 sources 事件,再逐段推 delta,结束自动关闭连接。 */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam("question") String question,
                                 @RequestParam(value = "webEnabled", defaultValue = "false") boolean webEnabled) {
        SseEmitter emitter = new SseEmitter(0L);
        executor.execute(() -> ragService.streamChat(
                question,
                webEnabled,
                chunk -> safeSend(emitter, "delta", chunk),
                sources -> safeSend(emitter, "sources", toJson(sources)),
                emitter::completeWithError,
                emitter::complete));
        return emitter;
    }

    /** AI 讲解:针对单个天体,结合知识库资料生成科普讲解。 */
    @GetMapping("/explain/{slug}")
    public ApiResponse<ExplainResponse> explain(@PathVariable String slug) throws Exception {
        return ApiResponse.ok(ragService.explain(slug));
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void safeSend(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
}

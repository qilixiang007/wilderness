package com.wilderness.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.ai.CompareService;
import com.wilderness.backend.ai.RagService;
import com.wilderness.backend.ai.agent.CelestialAgentService;
import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.ChatRequest;
import com.wilderness.backend.dto.ChatResponse;
import com.wilderness.backend.dto.ExplainResponse;
import com.wilderness.backend.dto.GenerateCelestialRequest;
import com.wilderness.backend.dto.GenerationResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/ai")
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class AiController {

    private final RagService ragService;
    private final CelestialAgentService celestialAgentService;
    private final CompareService compareService;
    private final ObjectMapper objectMapper;
    private final Executor streamExecutor;

    public AiController(RagService ragService,
                        CelestialAgentService celestialAgentService,
                        CompareService compareService,
                        ObjectMapper objectMapper,
                        @Qualifier("aiStreamExecutor") Executor streamExecutor) {
        this.ragService = ragService;
        this.celestialAgentService = celestialAgentService;
        this.compareService = compareService;
        this.objectMapper = objectMapper;
        this.streamExecutor = streamExecutor;
    }

    /** AI 问答:混合检索(+可选联网)+ 生成,返回回答与引文来源。 */
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) throws Exception {
        // 请求线程读取 userId(拦截器写入 AuthContext),传参给 service 做知识库隔离
        return ApiResponse.ok(ragService.chat(request.question(), request.webSearchEnabled(), AuthContext.currentUserId()));
    }

    /** AI 流式问答(SSE):先推 sources 事件,再逐段推 delta,结束自动关闭连接。 */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam("question") String question,
                                 @RequestParam(value = "webEnabled", defaultValue = "false") boolean webEnabled) {
        // 关键:检索发生在 executor 线程,ThreadLocal 不跨线程,必须在请求线程先取 userId
        Long userId = AuthContext.currentUserId();
        SseEmitter emitter = new SseEmitter(0L);
        try {
            streamExecutor.execute(() -> ragService.streamChat(
                    question,
                    webEnabled,
                    userId,
                    chunk -> safeSend(emitter, "delta", chunk),
                    sources -> safeSend(emitter, "sources", toJson(sources)),
                    emitter::completeWithError,
                    emitter::complete));
        } catch (RejectedExecutionException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** AI 讲解:针对单个天体,结合知识库资料生成科普讲解。 */
    @GetMapping("/explain/{slug}")
    public ApiResponse<ExplainResponse> explain(@PathVariable String slug) throws Exception {
        return ApiResponse.ok(ragService.explain(slug, AuthContext.currentUserId()));
    }

    /**
     * 多天体对比(SSE):并行生成各天体讲解,每篇一完成就推 item 事件(单路失败不影响其他),
     * 全部完成后再推 overview 事件(综合失败则 overview 为 null)。
     */
    @GetMapping(value = "/compare/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter compareStream(@RequestParam("slugs") String slugsParam) {
        List<String> slugs = Arrays.stream(slugsParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        Long userId = AuthContext.currentUserId();
        SseEmitter emitter = new SseEmitter(0L);
        try {
            streamExecutor.execute(() -> compareService.compareStream(
                    slugs,
                    userId,
                    item -> safeSend(emitter, "item", toJson(item)),
                    overview -> safeSend(emitter, "overview", toJson(Collections.singletonMap("overview", overview))),
                    emitter::completeWithError,
                    emitter::complete));
        } catch (RejectedExecutionException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** 天体生成 Agent:描述/参数 → 检索真实天体作参考 → 生成虚拟天体的介绍与渲染参数。半公开,未登录可生成(不落历史)。 */
    @PostMapping("/generate-celestial")
    public ApiResponse<GenerationResult> generateCelestial(@Valid @RequestBody GenerateCelestialRequest request) throws Exception {
        return ApiResponse.ok(celestialAgentService.generate(AuthContext.currentUserId(), request.description()));
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

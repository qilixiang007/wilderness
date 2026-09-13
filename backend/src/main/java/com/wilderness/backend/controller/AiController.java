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
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * chat/stream 的连接超时。这条链路没有任何内部超时兜底——模型层的 timeout 只盖到响应头,
     * 流一旦开始就不再约束——所以 emitter 超时是「模型吐了一半卡住」的唯一防线。
     * 取 180s 是给长回答留足余量;外层 nginx proxy_read_timeout 300s 是硬上界。
     */
    static final long CHAT_STREAM_TIMEOUT_MS = 180_000L;

    /**
     * compare/stream 的连接超时。CompareService 内部已有 itemTimeout(60s)+overviewTimeout(60s)
     * ≈120s 的上界,这里只做外层保险,必须大于它,否则会把正常的对比提前掐断。
     */
    static final long COMPARE_STREAM_TIMEOUT_MS = 150_000L;

    private final long chatStreamTimeoutMs;

    private final long compareStreamTimeoutMs;

    @Autowired
    public AiController(RagService ragService,
                        CelestialAgentService celestialAgentService,
                        CompareService compareService,
                        ObjectMapper objectMapper,
                        @Qualifier("aiStreamExecutor") Executor streamExecutor) {
        this(ragService, celestialAgentService, compareService, objectMapper, streamExecutor,
                CHAT_STREAM_TIMEOUT_MS, COMPARE_STREAM_TIMEOUT_MS);
    }

    /** 供测试注入更短的超时,避免真实等待 180s。 */
    AiController(RagService ragService,
                 CelestialAgentService celestialAgentService,
                 CompareService compareService,
                 ObjectMapper objectMapper,
                 Executor streamExecutor,
                 long chatStreamTimeoutMs,
                 long compareStreamTimeoutMs) {
        this.ragService = ragService;
        this.celestialAgentService = celestialAgentService;
        this.compareService = compareService;
        this.objectMapper = objectMapper;
        this.streamExecutor = streamExecutor;
        this.chatStreamTimeoutMs = chatStreamTimeoutMs;
        this.compareStreamTimeoutMs = compareStreamTimeoutMs;
    }

    /** AI 问答:混合检索(+可选联网)+ 生成,返回回答与引文来源。 */
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) throws Exception {
        // 请求线程读取 userId(拦截器写入 AuthContext),传参给 service 做知识库隔离
        return ApiResponse.ok(ragService.chat(request.question(), request.webSearchEnabled(), AuthContext.currentUserId()));
    }

    /**
     * AI 流式问答(SSE):检索失败会先推 retrieval-status 事件(仅降级时才推),
     * 再推 sources 事件,然后逐段推 delta,结束自动关闭连接。
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam("question") String question,
                                 @RequestParam(value = "webEnabled", defaultValue = "false") boolean webEnabled) {
        // 关键:检索发生在 executor 线程,ThreadLocal 不跨线程,必须在请求线程先取 userId
        Long userId = AuthContext.currentUserId();
        SseSession session = new SseSession(new SseEmitter(chatStreamTimeoutMs), "chat/stream");
        try {
            streamExecutor.execute(() -> ragService.streamChat(
                    question,
                    webEnabled,
                    userId,
                    chunk -> session.send("delta", chunk),
                    sources -> session.send("sources", toJson(sources)),
                    degraded -> {
                        if (degraded) {
                            session.send("retrieval-status", toJson(Collections.singletonMap("degraded", true)));
                        }
                    },
                    session::fail,
                    session::complete));
        } catch (RejectedExecutionException e) {
            session.fail(e);
        }
        return session.emitter();
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
        SseSession session = new SseSession(new SseEmitter(compareStreamTimeoutMs), "compare/stream");
        try {
            streamExecutor.execute(() -> compareService.compareStream(
                    slugs,
                    userId,
                    item -> session.send("item", toJson(item)),
                    overview -> session.send("overview", toJson(Collections.singletonMap("overview", overview))),
                    session::fail,
                    session::complete));
        } catch (RejectedExecutionException e) {
            session.fail(e);
        }
        return session.emitter();
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
}

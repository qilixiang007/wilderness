package com.wilderness.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.ai.CompareService;
import com.wilderness.backend.ai.RagService;
import com.wilderness.backend.ai.agent.CelestialAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * AiController 的 SSE 接线:超时值有没有真的传到 emitter、异常路径有没有收尾。
 * 闸门自身的行为由 {@link SseSessionTest} 覆盖;超时真正被触发是 Spring/Tomcat 的契约,不在这里重测。
 */
class AiControllerTest {

    private final RagService ragService = mock(RagService.class);

    private final CelestialAgentService agentService = mock(CelestialAgentService.class);

    private final CompareService compareService = mock(CompareService.class);

    private AiController controllerWith(Executor executor) {
        return new AiController(ragService, agentService, compareService, new ObjectMapper(), executor,
                AiController.CHAT_STREAM_TIMEOUT_MS, AiController.COMPARE_STREAM_TIMEOUT_MS);
    }

    /** 两个端点都不能再是 new SseEmitter(0L)——0 在 Spring 里表示永不超时。 */
    @Test
    void bothStreamEndpointsCarryANonZeroTimeout() {
        AiController controller = controllerWith(Runnable::run);

        assertEquals(AiController.CHAT_STREAM_TIMEOUT_MS, controller.chatStream("问题", false).getTimeout());
        assertEquals(AiController.COMPARE_STREAM_TIMEOUT_MS, controller.compareStream("a,b").getTimeout());
    }

    /**
     * compare 的外层保险必须大于它内部的预算(itemTimeout 60s + overviewTimeout 60s),
     * 否则正常的多天体对比会被 emitter 提前掐断。
     */
    @Test
    void compareTimeoutExceedsItsInternalBudget() {
        assertTrue(AiController.COMPARE_STREAM_TIMEOUT_MS > 120_000L,
                "compare 内部 join 最长约 120s,外层超时必须更大,否则会掐断正常对比");
    }

    /** 注入的超时要能覆盖默认值——测试靠这个避免真等 180s。 */
    @Test
    void injectedTimeoutOverridesDefaults() {
        AiController controller = new AiController(ragService, agentService, compareService,
                new ObjectMapper(), Runnable::run, 2_000L, 3_000L);

        assertEquals(2_000L, controller.chatStream("问题", false).getTimeout());
        assertEquals(3_000L, controller.compareStream("a,b").getTimeout());
    }

    /** 线程池打满直接拒绝时,emitter 必须被收尾,不能留下一条永远挂着的连接。 */
    @Test
    void rejectedExecutionClosesEmitter() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("aiStreamExecutor 已满");
        };
        AiController controller = controllerWith(rejecting);

        SseEmitter emitter = controller.chatStream("问题", false);

        // 已收尾的 emitter 再写会抛 IllegalStateException,以此确认它确实被关掉了
        assertThrows(IllegalStateException.class, () -> emitter.send("后续内容"));
    }
}

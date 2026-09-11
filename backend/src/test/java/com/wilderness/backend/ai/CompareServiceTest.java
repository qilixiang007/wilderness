package com.wilderness.backend.ai;

import com.wilderness.backend.ai.trace.Tracer;
import com.wilderness.backend.dto.CompareItemResult;
import com.wilderness.backend.dto.ExplainResponse;
import com.wilderness.backend.dto.ObjectDetailDTO;
import com.wilderness.backend.service.CelestialObjectService;
import com.wilderness.backend.service.CompareHistoryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CompareService 纯单元测试:mock RagService/CelestialObjectService/AiAssistant,
 * 真实线程池验证并行编排、逐项推送、单路降级、综合失败、超时兜底。不依赖 Spring/ES/真实 LLM。
 * compareStream 内部把所有 CompletableFuture join 完才返回,所以测试方法调用它时是同步阻塞的——
 * 断言时全部回调都已经发生过，不需要额外同步。收集回调结果的容器仍用线程安全类型，
 * 因为 core=max=4 时多个 onItem 可能从不同的 executor 线程并发调用。
 */
class CompareServiceTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private ObjectDetailDTO detail(String slug, String zhName, String enName) {
        return new ObjectDetailDTO(slug, zhName, enName, null, null, null, 0, null, List.of(), null, null, null);
    }

    private record Recorder(List<CompareItemResult> items, AtomicReference<String> overview,
                             AtomicReference<Throwable> error, AtomicBoolean done) {
        static Recorder create() {
            return new Recorder(new CopyOnWriteArrayList<>(), new AtomicReference<>(), new AtomicReference<>(), new AtomicBoolean(false));
        }
    }

    private void run(CompareService service, List<String> slugs, Long userId, Recorder r) {
        service.compareStream(slugs, userId, r.items()::add, r.overview()::set, r.error()::set, () -> r.done().set(true));
    }

    @Test
    void 并行调用各天体讲解且userId透传() throws Exception {
        RagService ragService = mock(RagService.class);
        CelestialObjectService objectService = mock(CelestialObjectService.class);
        AiAssistant assistant = mock(AiAssistant.class);

        when(objectService.findBySlug("earth")).thenReturn(detail("earth", "地球", "Earth"));
        when(objectService.findBySlug("mars")).thenReturn(detail("mars", "火星", "Mars"));
        when(ragService.explain(eq("earth"), eq(42L))).thenReturn(new ExplainResponse("地球讲解", List.of(), false));
        when(ragService.explain(eq("mars"), eq(42L))).thenReturn(new ExplainResponse("火星讲解", List.of(), false));
        when(assistant.compareOverview(anyString())).thenReturn("综合总结");

        CompareService service = new CompareService(ragService, objectService, assistant, mock(CompareHistoryService.class), Tracer.noop(), executor, 60, 60);
        Recorder r = Recorder.create();
        run(service, List.of("earth", "mars"), 42L, r);

        assertEquals(2, r.items().size());
        assertEquals("综合总结", r.overview().get());
        assertNull(r.error().get());
        assertTrue(r.done().get());
        verify(ragService).explain("earth", 42L);
        verify(ragService).explain("mars", 42L);
    }

    @Test
    void 单个天体无资料时该项降级其余正常() throws Exception {
        RagService ragService = mock(RagService.class);
        CelestialObjectService objectService = mock(CelestialObjectService.class);
        AiAssistant assistant = mock(AiAssistant.class);

        when(objectService.findBySlug("earth")).thenReturn(detail("earth", "地球", "Earth"));
        when(objectService.findBySlug("ghost")).thenReturn(detail("ghost", "幽灵星", "Ghost"));
        when(ragService.explain(eq("earth"), any())).thenReturn(new ExplainResponse("地球讲解", List.of(), false));
        when(ragService.explain(eq("ghost"), any())).thenThrow(new IllegalArgumentException("无资料"));
        when(assistant.compareOverview(anyString())).thenReturn("综合总结");

        CompareService service = new CompareService(ragService, objectService, assistant, mock(CompareHistoryService.class), Tracer.noop(), executor, 60, 60);
        Recorder r = Recorder.create();
        run(service, List.of("earth", "ghost"), null, r);

        CompareItemResult ghostItem = r.items().stream().filter(i -> i.slug().equals("ghost")).findFirst().orElseThrow();
        assertNotNull(ghostItem.error());
        CompareItemResult earthItem = r.items().stream().filter(i -> i.slug().equals("earth")).findFirst().orElseThrow();
        assertNull(earthItem.error());
        assertEquals("综合总结", r.overview().get());
        verify(assistant, times(1)).compareOverview(anyString());
    }

    @Test
    void 全部失败时不发起综合调用且overview为null() throws Exception {
        RagService ragService = mock(RagService.class);
        CelestialObjectService objectService = mock(CelestialObjectService.class);
        AiAssistant assistant = mock(AiAssistant.class);

        when(objectService.findBySlug(anyString())).thenReturn(detail("x", "x", "x"));
        when(ragService.explain(anyString(), any())).thenThrow(new IllegalArgumentException("无资料"));

        CompareService service = new CompareService(ragService, objectService, assistant, mock(CompareHistoryService.class), Tracer.noop(), executor, 60, 60);
        Recorder r = Recorder.create();
        run(service, List.of("a", "b"), null, r);

        assertTrue(r.items().stream().allMatch(i -> i.error() != null));
        assertNull(r.overview().get());
        assertTrue(r.done().get());
        verify(assistant, never()).compareOverview(anyString());
    }

    @Test
    void 综合调用失败时overview为null但items保留() throws Exception {
        RagService ragService = mock(RagService.class);
        CelestialObjectService objectService = mock(CelestialObjectService.class);
        AiAssistant assistant = mock(AiAssistant.class);

        when(objectService.findBySlug(anyString())).thenReturn(detail("earth", "地球", "Earth"));
        when(ragService.explain(anyString(), any())).thenReturn(new ExplainResponse("讲解", List.of(), false));
        when(assistant.compareOverview(anyString())).thenThrow(new RuntimeException("LLM 调用失败"));

        CompareService service = new CompareService(ragService, objectService, assistant, mock(CompareHistoryService.class), Tracer.noop(), executor, 60, 60);
        Recorder r = Recorder.create();
        run(service, List.of("earth", "mars"), null, r);

        assertNull(r.overview().get());
        assertEquals(2, r.items().size());
        assertTrue(r.items().stream().noneMatch(i -> i.error() != null));
    }

    @Test
    void 单路挂死超时后降级不阻塞整体() throws Exception {
        RagService ragService = mock(RagService.class);
        CelestialObjectService objectService = mock(CelestialObjectService.class);
        AiAssistant assistant = mock(AiAssistant.class);

        when(objectService.findBySlug("earth")).thenReturn(detail("earth", "地球", "Earth"));
        when(objectService.findBySlug("stuck")).thenReturn(detail("stuck", "卡死星", "Stuck"));
        when(ragService.explain(eq("earth"), any())).thenReturn(new ExplainResponse("讲解", List.of(), false));
        CountDownLatch latch = new CountDownLatch(1);
        when(ragService.explain(eq("stuck"), any())).thenAnswer(inv -> {
            latch.await(10, TimeUnit.SECONDS); // 模拟挂死;1s 超时会先于这里的 10s 触发
            return new ExplainResponse("不应到达", List.of(), false);
        });
        when(assistant.compareOverview(anyString())).thenReturn("综合总结");

        CompareService service = new CompareService(ragService, objectService, assistant, mock(CompareHistoryService.class), Tracer.noop(), executor, 1, 1);
        Recorder r = Recorder.create();
        long start = System.currentTimeMillis();
        run(service, List.of("earth", "stuck"), null, r);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 5000, "超时兜底应在秒级完成,而不是等到挂死线程真正结束");
        CompareItemResult stuckItem = r.items().stream().filter(i -> i.slug().equals("stuck")).findFirst().orElseThrow();
        assertNotNull(stuckItem.error());
        latch.countDown();
    }

    @Test
    void 少于2个不同天体触发onError且不推送任何item() throws Exception {
        RagService ragService = mock(RagService.class);
        CelestialObjectService objectService = mock(CelestialObjectService.class);
        AiAssistant assistant = mock(AiAssistant.class);

        CompareService service = new CompareService(ragService, objectService, assistant, mock(CompareHistoryService.class), Tracer.noop(), executor, 60, 60);
        Recorder r = Recorder.create();
        run(service, List.of("earth", "earth"), null, r);

        assertNotNull(r.error().get());
        assertTrue(r.items().isEmpty());
        assertFalse(r.done().get());
    }
}

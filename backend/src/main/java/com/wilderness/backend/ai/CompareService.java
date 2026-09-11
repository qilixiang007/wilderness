package com.wilderness.backend.ai;

import com.wilderness.backend.dto.CompareItemResult;
import com.wilderness.backend.dto.ExplainResponse;
import com.wilderness.backend.dto.ObjectDetailDTO;
import com.wilderness.backend.service.CelestialObjectService;
import com.wilderness.backend.service.CompareHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 多天体对比编排(流式):并行生成各天体讲解,每篇一完成就通过 onItem 推送,
 * 全部完成后再综合成一段跨天体对比短文并通过 onOverview 推送。
 * 单路失败(无资料/生成出错/超时)只降级该项,绝不让整批中断;
 * 综合调用失败同理只让 overview 为 null,不影响已推送的各篇讲解。
 */
@Service
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class CompareService {

    private static final Logger log = LoggerFactory.getLogger(CompareService.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    private final RagService ragService;
    private final CelestialObjectService celestialObjectService;
    private final AiAssistant assistant;
    private final CompareHistoryService compareHistoryService;
    private final Executor aiCompareExecutor;
    private final long itemTimeoutSeconds;
    private final long overviewTimeoutSeconds;

    @Autowired
    public CompareService(RagService ragService,
                          CelestialObjectService celestialObjectService,
                          AiAssistant assistant,
                          CompareHistoryService compareHistoryService,
                          @Qualifier("aiCompareExecutor") Executor aiCompareExecutor) {
        this(ragService, celestialObjectService, assistant, compareHistoryService, aiCompareExecutor, DEFAULT_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
    }

    /** 供测试注入更短的超时,避免真实等待 60s 才能验证超时降级路径。 */
    CompareService(RagService ragService,
                   CelestialObjectService celestialObjectService,
                   AiAssistant assistant,
                   CompareHistoryService compareHistoryService,
                   Executor aiCompareExecutor,
                   long itemTimeoutSeconds,
                   long overviewTimeoutSeconds) {
        this.ragService = ragService;
        this.celestialObjectService = celestialObjectService;
        this.assistant = assistant;
        this.compareHistoryService = compareHistoryService;
        this.aiCompareExecutor = aiCompareExecutor;
        this.itemTimeoutSeconds = itemTimeoutSeconds;
        this.overviewTimeoutSeconds = overviewTimeoutSeconds;
    }

    /**
     * @param onItem     每个天体的讲解一完成(成功或降级)就回调一次,顺序=完成顺序而非请求顺序
     * @param onOverview 全部讲解完成后回调一次,传入综合总结(失败为 null)
     * @param onError    校验失败或未预期异常时回调,调用方应结束整个流程
     * @param onDone     正常走完全流程(已调过 onOverview)后回调
     */
    public void compareStream(List<String> slugs,
                              Long userId,
                              Consumer<CompareItemResult> onItem,
                              Consumer<String> onOverview,
                              Consumer<Throwable> onError,
                              Runnable onDone) {
        try {
            List<String> uniq = List.copyOf(new LinkedHashSet<>(slugs));
            if (uniq.size() < 2) {
                onError.accept(new IllegalArgumentException("请至少选择 2 个不同的天体"));
                return;
            }

            List<CompletableFuture<CompareItemResult>> futures = uniq.stream()
                    .map(slug -> CompletableFuture.supplyAsync(() -> safeExplain(slug, userId), aiCompareExecutor)
                            .orTimeout(itemTimeoutSeconds, TimeUnit.SECONDS)
                            .exceptionally(e -> CompareItemResult.failed(slug, "讲解生成超时,请稍后重试"))
                            .thenApply(item -> {
                                onItem.accept(item);
                                return item;
                            }))
                    .toList();
            // 每个 future 已被上面的 exceptionally 兜底,join 不会再抛异常；此时 onItem 均已回调过
            List<CompareItemResult> items = futures.stream().map(CompletableFuture::join).toList();

            String overview = buildOverview(items)
                    .orTimeout(overviewTimeoutSeconds, TimeUnit.SECONDS)
                    .exceptionally(e -> null)
                    .join();
            compareHistoryService.save(userId, items, overview);
            onOverview.accept(overview);
            onDone.run();
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    /** 单个天体的讲解生成,任何异常都在此兜底降级为该项失败,绝不向上抛。 */
    private CompareItemResult safeExplain(String slug, Long userId) {
        try {
            ObjectDetailDTO object = celestialObjectService.findBySlug(slug);
            ExplainResponse explain = ragService.explain(slug, userId);
            return CompareItemResult.ok(slug, object.zhName(), object.enName(), explain.answer(), explain.sources(),
                    explain.retrievalDegraded());
        } catch (IllegalArgumentException e) {
            return CompareItemResult.failed(slug, "该天体暂无知识库资料,无法生成讲解");
        } catch (ResponseStatusException e) {
            return CompareItemResult.failed(slug, "天体不存在: " + slug);
        } catch (Exception e) {
            log.warn("compare: slug={} 讲解生成失败", slug, e);
            return CompareItemResult.failed(slug, "讲解生成失败,请稍后重试");
        }
    }

    /** 只对成功项做综合;全部失败则直接返回 null,不发起 LLM 调用。 */
    private CompletableFuture<String> buildOverview(List<CompareItemResult> items) {
        List<CompareItemResult> ok = items.stream().filter(i -> i.error() == null).toList();
        if (ok.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        String articles = ok.stream()
                .map(i -> "§ " + i.zhName() + "\n" + i.answer())
                .collect(Collectors.joining("\n\n"));
        return CompletableFuture.supplyAsync(() -> assistant.compareOverview(articles), aiCompareExecutor);
    }
}

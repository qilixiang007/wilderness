package com.wilderness.backend.ai;

import com.wilderness.backend.dto.CompareItemResult;
import com.wilderness.backend.dto.CompareResult;
import com.wilderness.backend.dto.ExplainResponse;
import com.wilderness.backend.dto.ObjectDetailDTO;
import com.wilderness.backend.service.CelestialObjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 多天体对比编排:并行生成各天体讲解,再综合成一段跨天体对比短文。
 * 单路失败(无资料/生成出错/超时)只降级该项,绝不让整批请求 500;
 * 综合调用失败同理只让 overview 为 null,不影响已生成的各篇讲解。
 */
@Service
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class CompareService {

    private static final Logger log = LoggerFactory.getLogger(CompareService.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    private final RagService ragService;
    private final CelestialObjectService celestialObjectService;
    private final AiAssistant assistant;
    private final Executor aiCompareExecutor;
    private final long itemTimeoutSeconds;
    private final long overviewTimeoutSeconds;

    @Autowired
    public CompareService(RagService ragService,
                          CelestialObjectService celestialObjectService,
                          AiAssistant assistant,
                          @Qualifier("aiCompareExecutor") Executor aiCompareExecutor) {
        this(ragService, celestialObjectService, assistant, aiCompareExecutor, DEFAULT_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
    }

    /** 供测试注入更短的超时,避免真实等待 60s 才能验证超时降级路径。 */
    CompareService(RagService ragService,
                   CelestialObjectService celestialObjectService,
                   AiAssistant assistant,
                   Executor aiCompareExecutor,
                   long itemTimeoutSeconds,
                   long overviewTimeoutSeconds) {
        this.ragService = ragService;
        this.celestialObjectService = celestialObjectService;
        this.assistant = assistant;
        this.aiCompareExecutor = aiCompareExecutor;
        this.itemTimeoutSeconds = itemTimeoutSeconds;
        this.overviewTimeoutSeconds = overviewTimeoutSeconds;
    }

    public CompareResult compare(List<String> slugs, Long userId) {
        List<String> uniq = List.copyOf(new LinkedHashSet<>(slugs));
        if (uniq.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少选择 2 个不同的天体");
        }

        List<CompletableFuture<CompareItemResult>> futures = uniq.stream()
                .map(slug -> CompletableFuture.supplyAsync(() -> safeExplain(slug, userId), aiCompareExecutor)
                        .orTimeout(itemTimeoutSeconds, TimeUnit.SECONDS)
                        .exceptionally(e -> CompareItemResult.failed(slug, "讲解生成超时,请稍后重试")))
                .toList();
        // 每个 future 已被上面的 exceptionally 兜底,join 不会再抛异常
        List<CompareItemResult> items = futures.stream().map(CompletableFuture::join).toList();

        String overview = buildOverview(items)
                .orTimeout(overviewTimeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(e -> null)
                .join();
        return new CompareResult(items, overview);
    }

    /** 单个天体的讲解生成,任何异常都在此兜底降级为该项失败,绝不向上抛。 */
    private CompareItemResult safeExplain(String slug, Long userId) {
        try {
            ObjectDetailDTO object = celestialObjectService.findBySlug(slug);
            ExplainResponse explain = ragService.explain(slug, userId);
            return CompareItemResult.ok(slug, object.zhName(), object.enName(), explain.answer(), explain.sources());
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

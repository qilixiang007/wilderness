package com.wilderness.backend.ai.langsmith;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 轻量 LangSmith REST 追踪客户端（自写，官方无 Java SDK）。
 *
 * 基本追踪流程：
 * - 创建 run：POST /runs，header x-api-key，body 含 id/name/run_type/inputs/start_time/session_name；
 * - 完成收尾：PATCH /runs/{id}，body 含 outputs + end_time（失败时带 error）；
 * - 无需自行计算 trace_id/dotted_order，服务端自动生成。
 *
 * 设计要点：
 * - 所有上报用 HttpClient.sendAsync 异步 fire-and-forget，2s 超时；
 * - 任何异常只记 debug 日志，绝不拖垮主业务；
 * - 未配置 LANGSMITH_API_KEY 时自动降级为 no-op。
 */
@Component
public class LangSmithTracer {

    private static final Logger log = LoggerFactory.getLogger(LangSmithTracer.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;
    private final String project;
    private final HttpClient http;

    /** Spring 构造：使用默认 HttpClient。多构造时需 @Autowired 指明。 */
    @Autowired
    public LangSmithTracer(ObjectMapper objectMapper,
                           @Value("${wilderness.ai.langsmith.endpoint}") String endpoint,
                           @Value("${wilderness.ai.langsmith.api-key}") String apiKey,
                           @Value("${wilderness.ai.langsmith.project}") String project) {
        this(objectMapper, endpoint, apiKey, project,
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    /** 包内可见构造：测试可注入 mock HttpClient。 */
    LangSmithTracer(ObjectMapper objectMapper, String endpoint, String apiKey, String project, HttpClient http) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.project = project;
        this.http = http;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 创建一条 run 并上报（异步）。未启用时返回 disabled 占位。 */
    public RunRef start(String name, String runType, Map<String, Object> inputs) {
        if (!isEnabled()) {
            return RunRef.disabled();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", UUID.randomUUID().toString());
        body.put("name", name);
        body.put("run_type", runType);
        body.put("inputs", inputs);
        body.put("start_time", Instant.now().toString());
        body.put("session_name", project);
        sendAsync("POST", "/runs", body);
        return new RunRef(true, (String) body.get("id"), name, runType);
    }

    /** run 成功收尾：上报 outputs + end_time。 */
    public void finish(RunRef run, Map<String, Object> outputs) {
        if (!run.enabled()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outputs", outputs);
        body.put("end_time", Instant.now().toString());
        sendAsync("PATCH", "/runs/" + run.runId(), body);
    }

    /** run 失败收尾：上报 error + end_time。 */
    public void fail(RunRef run, Throwable error) {
        if (!run.enabled()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", String.valueOf(error.getMessage()));
        body.put("end_time", Instant.now().toString());
        sendAsync("PATCH", "/runs/" + run.runId(), body);
    }

    private void sendAsync(String method, String path, Map<String, Object> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + path))
                    .header("x-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .timeout(TIMEOUT)
                    .build();
            http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(t -> {
                        log.debug("LangSmith {} {} failed: {}", method, path, t.toString());
                        return null;
                    });
        } catch (Exception e) {
            // 序列化/URI 等异常：静默降级，不影响主业务
            log.debug("LangSmith {} {} skipped: {}", method, path, e.toString());
        }
    }
}

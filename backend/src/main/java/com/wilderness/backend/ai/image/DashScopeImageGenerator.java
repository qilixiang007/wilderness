package com.wilderness.backend.ai.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 阿里云百炼（DashScope）通义万相文生图，走原生异步接口（OpenAI 兼容端点不支持 images）：
 * 1. 提交任务：POST /services/aigc/text2image/image-synthesis，X-DashScope-Async: enable → task_id；
 * 2. 轮询任务：GET /tasks/{task_id}，每 10s 一次，SUCCEEDED 后取 results[0].url。
 *
 * 注意：生成的图片 URL 约 24 小时过期，demo 直接返回 URL 足够；生产应下载转存 OSS/本地。
 * 未配置 wilderness.ai.image.api-key 时本 bean 不加载，Agent 自动降级为 SVG 渲染。
 */
@Component
@ConditionalOnExpression("!('${wilderness.ai.image.api-key:}'.trim().isEmpty())")
public class DashScopeImageGenerator implements ImageGenerator {

    private static final String ENDPOINT = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
    private static final String TASKS_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(10);
    private static final long MAX_POLL_MS = 120_000; // 最多等 2 分钟

    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper objectMapper;

    /** Spring 构造：默认 HttpClient。 */
    @Autowired
    public DashScopeImageGenerator(@Value("${wilderness.ai.image.api-key}") String apiKey,
                                   @Value("${wilderness.ai.image.model}") String model) {
        this(apiKey, model, HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), new ObjectMapper());
    }

    /** 包内可见构造：测试可注入 mock HttpClient。 */
    DashScopeImageGenerator(String apiKey, String model, HttpClient http, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.http = http;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(String prompt) throws Exception {
        String submitJson = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "input", Map.of("prompt", prompt),
                "parameters", Map.of("n", 1, "size", "1024*1024")));

        JsonNode submit = objectMapper.readTree(post(ENDPOINT, submitJson, true));
        String taskId = submit.path("output").path("task_id").asText();
        if (taskId.isEmpty()) {
            throw new IllegalStateException("文生图任务提交失败：" + submit);
        }

        long deadline = System.currentTimeMillis() + MAX_POLL_MS;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL.toMillis());
            JsonNode task = objectMapper.readTree(get(TASKS_URL + taskId));
            String status = task.path("output").path("task_status").asText();
            if ("SUCCEEDED".equals(status)) {
                String url = task.path("output").path("results").get(0).path("url").asText();
                if (url.isEmpty()) {
                    throw new IllegalStateException("文生图成功但未返回图片 URL");
                }
                return url;
            }
            if ("FAILED".equals(status)) {
                throw new IllegalStateException("文生图失败：" + task.path("output").path("message").asText());
            }
            // RUNNING/PENDING 继续轮询
        }
        throw new IllegalStateException("文生图超时（120s）");
    }

    private String post(String url, String jsonBody, boolean asyncHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(TIMEOUT);
        if (asyncHeader) {
            builder.header("X-DashScope-Async", "enable");
        }
        return body(http.send(builder.build(), HttpResponse.BodyHandlers.ofString()));
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .timeout(TIMEOUT)
                .build();
        return body(http.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private String body(HttpResponse<String> response) throws Exception {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("DashScope 返回 " + response.statusCode() + "：" + response.body());
        }
        return response.body();
    }
}

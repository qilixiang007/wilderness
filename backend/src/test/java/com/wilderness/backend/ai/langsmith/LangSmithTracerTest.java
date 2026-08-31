package com.wilderness.backend.ai.langsmith;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * LangSmithTracer 单测：mock HttpClient,验证上报的 method/path/header/body。
 * 全部走包内构造注入 mock,不触发网络。
 */
class LangSmithTracerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LangSmithTracer tracer(HttpClient http, String apiKey) {
        return new LangSmithTracer(objectMapper, "https://api.smith.langchain.com", apiKey, "wilderness", http);
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<HttpResponse<Void>> okFuture() {
        return CompletableFuture.completedFuture(mock(HttpResponse.class));
    }

    /** 读一次 HttpRequest body（BodyPublisher 一次性订阅,只可调一次）。 */
    private static String readBody(HttpRequest req) throws Exception {
        HttpRequest.BodyPublisher publisher = req.bodyPublisher().orElseThrow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                out.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.get(2, TimeUnit.SECONDS);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void noKeyStartIsDisabledAndSendsNothing() {
        HttpClient http = mock(HttpClient.class);
        LangSmithTracer tracer = tracer(http, "");

        RunRef run = tracer.start("rag.chat", "chain", Map.of("question", "木星"));

        assertFalse(run.enabled());
        assertNull(run.runId());
        verifyNoInteractions(http);
    }

    @Test
    void startPostsRunWithRequiredFields() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any())).thenReturn(okFuture());
        LangSmithTracer tracer = tracer(http, "test-key");

        tracer.start("rag.chat", "chain", Map.of("question", "木星为什么是红的", "webSearchEnabled", true));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(captor.capture(), any());
        HttpRequest req = captor.getValue();
        assertEquals("POST", req.method());
        assertEquals("https://api.smith.langchain.com/runs", req.uri().toString());
        assertEquals("test-key", req.headers().firstValue("x-api-key").orElseThrow());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(readBody(req), Map.class);
        assertNotNull(body.get("id"));
        assertEquals("rag.chat", body.get("name"));
        assertEquals("chain", body.get("run_type"));
        assertEquals("wilderness", body.get("session_name"));
        assertTrue(body.containsKey("start_time"));
        assertEquals("木星为什么是红的", ((Map<?, ?>) body.get("inputs")).get("question"));
    }

    @Test
    void finishPatchesRunWithOutputs() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any())).thenReturn(okFuture());
        LangSmithTracer tracer = tracer(http, "test-key");

        tracer.finish(new RunRef(true, "run-123", "rag.chat", "chain"),
                Map.of("answer", "木星是气态巨行星", "sourceCount", 3));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(captor.capture(), any());
        HttpRequest req = captor.getValue();
        assertEquals("PATCH", req.method());
        assertEquals("https://api.smith.langchain.com/runs/run-123", req.uri().toString());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(readBody(req), Map.class);
        assertEquals("木星是气态巨行星", ((Map<?, ?>) body.get("outputs")).get("answer"));
        assertEquals(3, ((Map<?, ?>) body.get("outputs")).get("sourceCount"));
        assertTrue(body.containsKey("end_time"));
    }

    @Test
    void failPatchesRunWithError() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any())).thenReturn(okFuture());
        LangSmithTracer tracer = tracer(http, "test-key");

        tracer.fail(new RunRef(true, "run-456", "rag.chat", "chain"),
                new IllegalStateException("知识库检索失败"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(captor.capture(), any());
        HttpRequest req = captor.getValue();
        assertEquals("PATCH", req.method());
        assertEquals("https://api.smith.langchain.com/runs/run-456", req.uri().toString());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(readBody(req), Map.class);
        assertEquals("知识库检索失败", body.get("error"));
        assertTrue(body.containsKey("end_time"));
    }

    @Test
    void httpFailureIsSwallowedAndDoesNotBreakCaller() {
        HttpClient http = mock(HttpClient.class);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        LangSmithTracer tracer = tracer(http, "test-key");

        RunRef run = tracer.start("rag.chat", "chain", Map.of("question", "q"));
        assertTrue(run.enabled());
        assertDoesNotThrow(() -> tracer.finish(run, Map.of("answer", "a")));
    }
}

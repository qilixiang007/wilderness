package com.wilderness.backend.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SSE 收尾闸门。核心不变式:一旦收尾(正常结束/出错/超时),就再也不碰 emitter。
 *
 * <p>为什么这条不变式非有不可:TokenStream 没有 cancel,compareStream 内部 join 最长 ~120s 才醒,
 * 收尾之后业务线程还会继续吐数据。这些迟到的回调若直接 send,SseEmitter 会抛 IllegalStateException。
 */
class SseSessionTest {

    private final SseEmitter emitter = mock(SseEmitter.class);

    /** 正常结束之后迟到的数据必须被丢弃。 */
    @Test
    void sendAfterCompleteIsDropped() throws Exception {
        SseSession session = new SseSession(emitter, "chat/stream");
        session.complete();

        session.send("delta", "迟到的 token");

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        assertTrue(session.isDone());
    }

    /**
     * 超时由容器在另一条线程上触发。这里捕获注册进去的回调手动触发,
     * 验证它会关闭连接并落闸——这是「模型吐了一半卡住」时唯一的出口。
     */
    @Test
    void timeoutClosesConnectionAndDropsLateData() throws Exception {
        SseSession session = new SseSession(emitter, "chat/stream");
        ArgumentCaptor<Runnable> timeoutCallback = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onTimeout(timeoutCallback.capture());
        assertFalse(session.isDone());

        timeoutCallback.getValue().run();

        assertTrue(session.isDone());
        verify(emitter, times(1)).complete();

        // 超时后模型仍在吐 token,必须被挡住
        session.send("delta", "超时之后才到的 token");
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    /** 容器报连接异常(客户端断开)后同样落闸。 */
    @Test
    void errorCallbackClosesGate() throws Exception {
        SseSession session = new SseSession(emitter, "compare/stream");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> errorCallback = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onError(errorCallback.capture());

        errorCallback.getValue().accept(new IllegalStateException("客户端断开"));

        assertTrue(session.isDone());
        session.send("item", "{}");
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    /** 收尾只认第一次:重复 complete 不该重复通知容器。 */
    @Test
    void completeIsIdempotent() {
        SseSession session = new SseSession(emitter, "chat/stream");

        session.complete();
        session.complete();

        verify(emitter, times(1)).complete();
    }

    /** 已正常结束的流,不能被迟到的异常改写成失败。 */
    @Test
    void failAfterCompleteDoesNotOverrideResult() {
        SseSession session = new SseSession(emitter, "chat/stream");
        session.complete();

        session.fail(new IllegalStateException("迟到的异常"));

        verify(emitter, never()).completeWithError(any());
    }

    /** send 失败(通常是客户端已断开)按收尾处理,并且只收尾一次。 */
    @Test
    void sendFailureCompletesWithErrorAndClosesGate() throws Exception {
        SseSession session = new SseSession(emitter, "chat/stream");
        doThrow(new IOException("Broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        session.send("delta", "写不出去的内容");

        assertTrue(session.isDone());
        verify(emitter, times(1)).completeWithError(any(IOException.class));

        // 闸门落下后不再尝试写第二次
        session.send("delta", "后续内容");
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }
}

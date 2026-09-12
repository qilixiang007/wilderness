package com.wilderness.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一条 SSE 连接的收尾闸门。
 *
 * <p>SSE 收尾(正常结束/出错/超时)之后,生产数据的业务线程往往还在跑:
 * LangChain4j 的 TokenStream 没有 cancel,模型的 HTTP 回调线程会继续吐 token;
 * CompareService.compareStream 内部 join 最长要 ~120s 才醒,醒来后照样推 item 和 overview。
 * 这些迟到的回调若直接写进已 complete 的 emitter,SseEmitter.send 会抛 IllegalStateException。
 *
 * <p>所以把「emitter + 是否已收尾」绑成一个对象:写之前先看闸门,收尾只认第一次。
 */
class SseSession {

    private static final Logger log = LoggerFactory.getLogger(SseSession.class);

    private final SseEmitter emitter;

    /** 仅用于日志定位是哪个接口,不参与逻辑。 */
    private final String path;

    private final AtomicBoolean done = new AtomicBoolean(false);

    SseSession(SseEmitter emitter, String path) {
        this.emitter = emitter;
        this.path = path;
        emitter.onTimeout(this::handleTimeout);
        emitter.onError(this::handleError);
        // 正常 complete 也要落闸:此后所有迟到回调一律丢弃
        emitter.onCompletion(() -> done.set(true));
    }

    SseEmitter emitter() {
        return emitter;
    }

    /** 已收尾就直接丢弃,不再触碰 emitter。 */
    void send(String eventName, String data) {
        if (done.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            // 客户端断开是常态(关页面、切路由),按收尾处理即可
            fail(e);
        }
    }

    void complete() {
        if (done.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    void fail(Throwable e) {
        if (done.compareAndSet(false, true)) {
            emitter.completeWithError(e);
        }
    }

    boolean isDone() {
        return done.get();
    }

    /**
     * 超时由容器在另一条线程上触发。此时业务线程通常还在跑,但我们不再等它——
     * 上游卡死时它可能永远不回来,连接不能一直挂着。
     */
    private void handleTimeout() {
        if (done.compareAndSet(false, true)) {
            log.warn("SSE 超时,主动关闭连接 path={}", path);
            emitter.complete();
        }
    }

    private void handleError(Throwable e) {
        done.set(true);
        log.warn("SSE 连接异常 path={}", path, e);
    }
}

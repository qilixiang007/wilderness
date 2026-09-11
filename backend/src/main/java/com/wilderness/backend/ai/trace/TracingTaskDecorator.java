package com.wilderness.backend.ai.trace;

import org.springframework.core.task.TaskDecorator;

/**
 * 线程池任务装饰器：提交任务时捕获当前 span，在工作线程上恢复，执行完再还原。
 * 多天体对比的并行 explain 由此成为对比 trace 的子树。
 */
public class TracingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Span captured = TraceContextHolder.current();
        if (captured == null || captured.isNoop()) {
            return runnable;
        }
        return () -> {
            Span previous = TraceContextHolder.swap(captured);
            try {
                runnable.run();
            } finally {
                TraceContextHolder.swap(previous);
            }
        };
    }
}

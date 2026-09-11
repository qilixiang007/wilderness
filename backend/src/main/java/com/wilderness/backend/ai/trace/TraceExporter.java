package com.wilderness.backend.ai.trace;

/**
 * trace 完成后的出口。本地存储、LangSmith 等各自实现，由 {@link TraceDispatcher} 分发。
 * 实现必须非阻塞（入队或异步发送）：export 运行在业务线程上。
 */
public interface TraceExporter {

    void export(TraceRecord trace);
}

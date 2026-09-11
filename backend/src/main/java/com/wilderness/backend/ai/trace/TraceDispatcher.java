package com.wilderness.backend.ai.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/** 把完成的 trace 分发给所有 exporter；单个 exporter 出错只记日志，不影响其他出口和业务。 */
@Component
public class TraceDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TraceDispatcher.class);

    private final List<TraceExporter> exporters;

    public TraceDispatcher(ObjectProvider<TraceExporter> exporters) {
        this.exporters = exporters.orderedStream().toList();
    }

    public void dispatch(TraceRecord trace) {
        for (TraceExporter exporter : exporters) {
            try {
                exporter.export(trace);
            } catch (Exception e) {
                log.warn("trace 导出失败 exporter={} traceId={}: {}",
                        exporter.getClass().getSimpleName(), trace.traceId(), e.toString());
            }
        }
    }
}

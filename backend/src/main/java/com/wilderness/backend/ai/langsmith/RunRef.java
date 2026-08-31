package com.wilderness.backend.ai.langsmith;

/**
 * 一次 LangSmith run 的最小上下文快照。
 * run 创建后由调用线程持有，完成/失败时传给 {@link LangSmithTracer#finish}/{@link LangSmithTracer#fail} 收尾。
 * 不可变 record，可在异步回调线程安全使用。
 */
public record RunRef(boolean enabled, String runId, String name, String runType) {

    /** 未启用追踪时的占位，所有操作空转。 */
    public static RunRef disabled() {
        return new RunRef(false, null, null, null);
    }
}

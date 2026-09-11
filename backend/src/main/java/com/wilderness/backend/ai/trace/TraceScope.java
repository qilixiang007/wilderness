package com.wilderness.backend.ai.trace;

/** {@link Span#makeCurrent()} 的返回值：close 时把线程上的「当前 span」恢复为进入前的值。 */
@FunctionalInterface
public interface TraceScope extends AutoCloseable {

    TraceScope NOOP = () -> { };

    @Override
    void close();
}

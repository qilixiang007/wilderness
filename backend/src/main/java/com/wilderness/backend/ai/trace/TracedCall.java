package com.wilderness.backend.ai.trace;

/** 被 span 包裹执行的一段业务代码，允许抛受检异常。 */
@FunctionalInterface
public interface TracedCall<T> {

    T call() throws Exception;
}

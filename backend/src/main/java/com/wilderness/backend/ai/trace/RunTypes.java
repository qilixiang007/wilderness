package com.wilderness.backend.ai.trace;

/**
 * span 的 run_type 取值，与 LangSmith run 格式保持一致，
 * 这样同一份数据既能喂给自建面板，也能原样上报 LangSmith。
 */
public final class RunTypes {

    public static final String CHAIN = "chain";
    public static final String LLM = "llm";
    public static final String RETRIEVER = "retriever";
    public static final String TOOL = "tool";
    public static final String EMBEDDING = "embedding";
    public static final String PARSER = "parser";

    private RunTypes() {
    }
}

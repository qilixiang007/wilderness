package com.wilderness.backend.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化分批工具。
 *
 * 实测 qwen text-embedding-v3 单次 embedAll 的 batch 上限为 10（超出返回
 * "batch size is invalid, it should not be larger than 10"）。知识库语料切块后
 * 通常远超 10，必须分批调用再合并，否则灌库/上传会整体失败。
 */
public final class EmbeddingBatchHelper {

    /** qwen text-embedding-v3 单次嵌入的 batch 上限。 */
    public static final int MAX_BATCH = 10;

    private EmbeddingBatchHelper() {
    }

    /** 分批嵌入所有文本段，返回与输入顺序一致的向量列表。 */
    public static List<Embedding> embedAll(EmbeddingModel model, List<TextSegment> segments) {
        List<Embedding> out = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i += MAX_BATCH) {
            List<TextSegment> batch = segments.subList(i, Math.min(i + MAX_BATCH, segments.size()));
            out.addAll(model.embedAll(batch).content());
        }
        return out;
    }
}

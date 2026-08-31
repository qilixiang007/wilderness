package com.wilderness.backend.ai;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库入库管线:读取 classpath 下的 Markdown 语料 → 切块 → 向量化 → 批量写入 ES。
 * 文档 ID 使用「文件名#块序号」,重复执行会覆盖同 ID,天然幂等,不会产生重复数据。
 */
@Service
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final ElasticsearchClient es;
    private final ElasticsearchIndexManager indexManager;
    private final EmbeddingModel embeddingModel;
    private final MarkdownFrontmatterParser parser;
    private final int chunkSize;
    private final int chunkOverlap;

    public KnowledgeIngestionService(ElasticsearchClient es,
                                     ElasticsearchIndexManager indexManager,
                                     EmbeddingModel embeddingModel,
                                     MarkdownFrontmatterParser parser,
                                     @Value("${wilderness.ai.rag.chunk-size}") int chunkSize,
                                     @Value("${wilderness.ai.rag.chunk-overlap}") int chunkOverlap) {
        this.es = es;
        this.indexManager = indexManager;
        this.embeddingModel = embeddingModel;
        this.parser = parser;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public void ingestFromClasspath() throws Exception {
        indexManager.ensureIndex();

        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:knowledge/*.md");
        if (resources.length == 0) {
            log.warn("未找到 classpath:knowledge/*.md 语料文件");
            return;
        }

        List<Document> documents = new ArrayList<>();
        for (Resource r : resources) {
            String fileName = r.getFilename();
            String text = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            MarkdownFrontmatterParser.ParsedDocument pd = parser.parse(text);
            Metadata meta = new Metadata()
                    .put("slug", pd.slug())
                    .put("type", pd.type())
                    .put("zh_name", pd.zhName())
                    .put("en_name", pd.enName())
                    .put("file_name", fileName);
            documents.add(Document.from(pd.content(), meta));
        }

        List<TextSegment> segments = DocumentSplitters.recursive(chunkSize, chunkOverlap).splitAll(documents);
        log.info("切块完成:{} 篇文档 → {} 个块(chunk-size={}, overlap={})",
                documents.size(), segments.size(), chunkSize, chunkOverlap);

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        List<Embedding> embeddings = response.content();
        if (embeddings.size() != segments.size()) {
            throw new IllegalStateException("向量数量与切块数量不一致:" + embeddings.size() + " vs " + segments.size());
        }
        int dim = embeddings.isEmpty() ? 0 : embeddings.get(0).vector().length;
        log.info("向量化完成:{} 个向量,维度 {}", embeddings.size(), dim);

        int written = ingestSegments(segments, embeddings, null);
        log.info("入库完成:索引 [{}] 共写入 {} 条记录", indexManager.indexName(), written);
    }

    /**
     * 将切块与向量写入 ES(公共复用:内置语料入库与用户上传共用)。
     * 文档 ID = [userId#]file_name#块序号,重复执行覆盖同 ID,幂等;
     * userId 前缀保证不同用户上传同名文件互不覆盖。
     * userId 为 null 表示内置公共语料,不写 user_id 字段(检索时对所有用户可见)。
     */
    public int ingestSegments(List<TextSegment> segments, List<Embedding> embeddings, Long userId) throws Exception {
        if (segments.size() != embeddings.size()) {
            throw new IllegalStateException("向量数量与切块数量不一致:" + embeddings.size() + " vs " + segments.size());
        }
        String index = indexManager.indexName();
        String idPrefix = userId == null ? "" : userId + "#";
        List<BulkOperation> ops = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment seg = segments.get(i);
            String id = idPrefix + seg.metadata().getString("file_name") + "#" + i;
            Map<String, Object> source = new HashMap<>();
            source.put("content", seg.text());
            source.put("content_vector", embeddings.get(i).vector());
            source.put("slug", seg.metadata().getString("slug"));
            source.put("type", seg.metadata().getString("type"));
            source.put("zh_name", seg.metadata().getString("zh_name"));
            source.put("en_name", seg.metadata().getString("en_name"));
            source.put("file_name", seg.metadata().getString("file_name"));
            source.put("chunk_index", i);
            if (userId != null) {
                source.put("user_id", String.valueOf(userId));
            }
            ops.add(BulkOperation.of(o -> o.index(io -> io.index(index).id(id).document(source))));
        }

        BulkResponse bulk = es.bulk(b -> b.operations(ops));
        if (bulk.errors()) {
            long failed = bulk.items().stream().filter(it -> it.error() != null).count();
            bulk.items().stream().filter(it -> it.error() != null).findFirst().ifPresent(it ->
                    log.error("批量写入失败示例:{}", it.error().reason()));
            throw new IllegalStateException("知识库批量写入失败 " + failed + " 条");
        }
        return ops.size();
    }
}

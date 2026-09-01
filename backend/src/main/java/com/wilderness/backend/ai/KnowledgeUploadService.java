package com.wilderness.backend.ai;

import com.wilderness.backend.ai.DocumentTextExtractor.Extracted;
import com.wilderness.backend.domain.KnowledgeDocument;
import com.wilderness.backend.dto.UploadResult;
import com.wilderness.backend.repository.KnowledgeDocumentRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/**
 * 用户上传文件入库:解析文本 → 切块 → 向量化 → 写入 ES 知识库。
 * 上传内容与内置语料共用索引,通过 type=upload 区分来源;user_id 归属当前登录用户,
 * 检索时按用户隔离。同时在 knowledge_document 表记录文件清单(「我的文件」用)。
 */
@Service
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class KnowledgeUploadService {

    private static final int MAX_TEXT_LENGTH = 1_000_000;

    private final EmbeddingModel embeddingModel;
    private final DocumentTextExtractor extractor;
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final int chunkSize;
    private final int chunkOverlap;

    public KnowledgeUploadService(EmbeddingModel embeddingModel,
                                  DocumentTextExtractor extractor,
                                  KnowledgeIngestionService ingestionService,
                                  KnowledgeDocumentRepository knowledgeDocumentRepository,
                                  @Value("${wilderness.ai.rag.chunk-size}") int chunkSize,
                                  @Value("${wilderness.ai.rag.chunk-overlap}") int chunkOverlap) {
        this.embeddingModel = embeddingModel;
        this.extractor = extractor;
        this.ingestionService = ingestionService;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    @Transactional
    public UploadResult upload(MultipartFile file, Long userId) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String fileName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        byte[] bytes = file.getBytes();

        Extracted extracted = extractor.extract(fileName, bytes);
        String text = extracted.text().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("未能从文件中解析出有效文本,无法入库");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("文件文本超过 100 万字符,请精简后重试");
        }

        Metadata meta = new Metadata()
                .put("slug", sanitizeSlug(fileName))
                .put("type", "upload")
                .put("zh_name", fileName)
                .put("en_name", fileName)
                .put("file_name", fileName);
        Document document = Document.from(text, meta);

        List<TextSegment> segments = DocumentSplitters.recursive(chunkSize, chunkOverlap).split(document);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("切块结果为空,无法入库");
        }

        // qwen 嵌入单次 batch 上限 10，分批后再合并
        List<Embedding> embeddings = EmbeddingBatchHelper.embedAll(embeddingModel, segments);
        int written = ingestionService.ingestSegments(segments, embeddings, userId);
        upsertDocument(userId, fileName, text.length(), written);
        return new UploadResult(fileName, extracted.type(), text.length(), written, Instant.now().toEpochMilli());
    }

    /** 文件清单 upsert:同用户同名文件复用同一行(重传覆盖 ES 后刷新统计)。 */
    private void upsertDocument(Long userId, String fileName, long charCount, int chunkCount) {
        KnowledgeDocument doc = knowledgeDocumentRepository.findByUserIdAndFileName(userId, fileName)
                .orElseGet(() -> new KnowledgeDocument(userId, fileName,
                        sanitizeSlug(fileName), extension(fileName), charCount, chunkCount));
        doc.refresh(charCount, chunkCount);
        knowledgeDocumentRepository.save(doc);
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    /** 文件名 → 知识库 slug(去扩展名,保留中文,其余转为连字符)。 */
    private String sanitizeSlug(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot < 0 ? fileName : fileName.substring(0, dot);
        String cleaned = base.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-");
        return cleaned.replaceAll("^-|-$", "");
    }
}

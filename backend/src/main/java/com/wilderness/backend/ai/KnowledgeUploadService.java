package com.wilderness.backend.ai;

import com.wilderness.backend.ai.DocumentTextExtractor.Extracted;
import com.wilderness.backend.dto.UploadResult;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/**
 * 用户上传文件入库:解析文本 → 切块 → 向量化 → 写入 ES 知识库。
 * 上传内容与内置语料共用索引,通过 type=upload 与上传时间等元数据区分来源。
 */
@Service
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class KnowledgeUploadService {

    private static final int MAX_TEXT_LENGTH = 1_000_000;

    private final EmbeddingModel embeddingModel;
    private final DocumentTextExtractor extractor;
    private final KnowledgeIngestionService ingestionService;
    private final int chunkSize;
    private final int chunkOverlap;

    public KnowledgeUploadService(EmbeddingModel embeddingModel,
                                  DocumentTextExtractor extractor,
                                  KnowledgeIngestionService ingestionService,
                                  @Value("${wilderness.ai.rag.chunk-size}") int chunkSize,
                                  @Value("${wilderness.ai.rag.chunk-overlap}") int chunkOverlap) {
        this.embeddingModel = embeddingModel;
        this.extractor = extractor;
        this.ingestionService = ingestionService;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public UploadResult upload(MultipartFile file, String uploader) throws Exception {
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
                .put("file_name", fileName)
                .put("uploader", uploader == null ? "anonymous" : uploader)
                .put("upload_time", Instant.now().toString());
        Document document = Document.from(text, meta);

        List<TextSegment> segments = DocumentSplitters.recursive(chunkSize, chunkOverlap).split(document);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("切块结果为空,无法入库");
        }

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        int written = ingestionService.ingestSegments(segments, response.content());
        return new UploadResult(fileName, extracted.type(), text.length(), written, Instant.now().toEpochMilli());
    }

    /** 文件名 → 知识库 slug(去扩展名,保留中文,其余转为连字符)。 */
    private String sanitizeSlug(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot < 0 ? fileName : fileName.substring(0, dot);
        String cleaned = base.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-");
        return cleaned.replaceAll("^-|-$", "");
    }
}

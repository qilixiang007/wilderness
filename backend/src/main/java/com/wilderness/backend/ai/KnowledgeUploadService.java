package com.wilderness.backend.ai;

import com.wilderness.backend.ai.DocumentTextExtractor.Extracted;
import com.wilderness.backend.ai.trace.RunTypes;
import com.wilderness.backend.ai.trace.Span;
import com.wilderness.backend.ai.trace.TraceAttrs;
import com.wilderness.backend.ai.trace.TraceScope;
import com.wilderness.backend.ai.trace.Tracer;
import com.wilderness.backend.domain.KnowledgeDocument;
import com.wilderness.backend.dto.BatchUploadResult;
import com.wilderness.backend.dto.UploadResult;
import com.wilderness.backend.repository.KnowledgeDocumentRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户上传文件入库:解析文本 → 切块 → 向量化 → 写入 ES 知识库。
 * 上传内容与内置语料共用索引,通过 type=upload 区分来源;user_id 归属当前登录用户,
 * 检索时按用户隔离。同时在 knowledge_document 表记录文件清单(「我的文件」用)。
 *
 * 对外入口是 {@link #uploadBatch}:一次请求提交一批文件(上限 {@value #MAX_BATCH_SIZE} 个),
 * 这样「一次提交」在上传限流计数和配额校验上都只算一次操作。
 */
@Service
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class KnowledgeUploadService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeUploadService.class);
    private static final int MAX_TEXT_LENGTH = 1_000_000;
    /** 个人知识库单用户最多文件数；重传已有同名文件（upsert 覆盖）不占用新名额。 */
    private static final int MAX_FILES_PER_USER = 20;
    private static final int MAX_BATCH_SIZE = 5;

    private final EmbeddingModel embeddingModel;
    private final DocumentTextExtractor extractor;
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final Tracer tracer;
    private final int chunkSize;
    private final int chunkOverlap;

    public KnowledgeUploadService(EmbeddingModel embeddingModel,
                                  DocumentTextExtractor extractor,
                                  KnowledgeIngestionService ingestionService,
                                  KnowledgeDocumentRepository knowledgeDocumentRepository,
                                  Tracer tracer,
                                  @Value("${wilderness.ai.rag.chunk-size}") int chunkSize,
                                  @Value("${wilderness.ai.rag.chunk-overlap}") int chunkOverlap) {
        this.embeddingModel = embeddingModel;
        this.extractor = extractor;
        this.ingestionService = ingestionService;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.tracer = tracer;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 批量入库:整批级问题(没选文件、超出单批上限、配额放不下整批)直接抛 400 整批拒绝,
     * 一个都不处理;进入循环后单个文件的失败(格式不支持、解析不出文本等)只标记该文件,
     * 不中断同批其它文件。
     *
     * 这里不做事务:ES 的向量块写入本就不在数据库事务内、无法回滚,MySQL 侧只有
     * upsertDocument 里一次 save(JpaRepository.save 自带事务),包一层事务既拦不住
     * 不一致、又会让「某个文件失败」连累整批已入库的行。
     */
    public BatchUploadResult uploadBatch(List<MultipartFile> files, Long userId) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未选择文件");
        }
        if (files.size() > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "单次最多上传 " + MAX_BATCH_SIZE + " 个文件,本次选择了 " + files.size() + " 个");
        }
        checkBatchQuota(files, userId);

        List<BatchUploadResult.Item> items = new ArrayList<>(files.size());
        int succeeded = 0;
        for (MultipartFile file : files) {
            String fileName = fileNameOf(file);
            try {
                items.add(BatchUploadResult.Item.ok(upload(file, userId)));
                succeeded++;
            } catch (Exception e) {
                log.warn("批量上传单个文件入库失败 userId={} fileName={}", userId, fileName, e);
                items.add(BatchUploadResult.Item.failed(fileName, reasonOf(e)));
            }
        }
        return new BatchUploadResult(succeeded, files.size() - succeeded, items);
    }

    /**
     * 配额按整批判断:已有文件数 + 本批「新文件名」数 > 上限就整批拒绝,而不是入库到满为止。
     * 与已有文件同名的属于覆盖重传,不占新名额;同一批里重名只算一个。
     * upload() 里还保留逐文件的配额检查,兜底同一用户并发提交两批时的竞态。
     */
    private void checkBatchQuota(List<MultipartFile> files, Long userId) {
        Set<String> existingNames = knowledgeDocumentRepository.findByUserIdOrderByUploadedAtDesc(userId).stream()
                .map(KnowledgeDocument::getFileName)
                .collect(Collectors.toSet());
        long newCount = files.stream()
                .map(this::fileNameOf)
                .distinct()
                .filter(name -> !existingNames.contains(name))
                .count();
        int used = existingNames.size();
        if (used + newCount > MAX_FILES_PER_USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "知识库最多 " + MAX_FILES_PER_USER + " 个文件,当前已有 " + used + " 个,本次新增 " + newCount
                            + " 个,还剩 " + Math.max(0, MAX_FILES_PER_USER - used) + " 个名额,请减少文件或先删除部分文件");
        }
    }

    private String fileNameOf(MultipartFile file) {
        return file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
    }

    private String reasonOf(Exception e) {
        String reason = e instanceof ResponseStatusException rse ? rse.getReason() : e.getMessage();
        return (reason == null || reason.isBlank()) ? "入库失败" : reason;
    }

    /** 单个文件入库。失败一律抛异常,由 {@link #uploadBatch} 捕获后转成该文件的失败项。 */
    private UploadResult upload(MultipartFile file, Long userId) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String fileName = fileNameOf(file);
        Span root = tracer.start("knowledge.upload", RunTypes.CHAIN,
                TraceAttrs.of("fileName", fileName, "sizeBytes", file.getSize()), userId);
        try (TraceScope ignored = root.makeCurrent()) {
            UploadResult result = doUpload(file, fileName, userId);
            root.end(TraceAttrs.of("type", result.type(), "charCount", result.charCount(), "chunkCount", result.chunkCount()));
            return result;
        } catch (Exception e) {
            root.fail(e);
            throw e;
        }
    }

    private UploadResult doUpload(MultipartFile file, String fileName, Long userId) throws Exception {
        Optional<KnowledgeDocument> existing = knowledgeDocumentRepository.findByUserIdAndFileName(userId, fileName);
        if (existing.isEmpty() && knowledgeDocumentRepository.countByUserId(userId) >= MAX_FILES_PER_USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "知识库文件数量已达上限（" + MAX_FILES_PER_USER + " 个），请先删除部分文件再上传");
        }
        byte[] bytes = file.getBytes();

        Extracted extracted = tracer.inChild("document.extract", RunTypes.PARSER,
                TraceAttrs.of("fileName", fileName, "sizeBytes", bytes.length),
                () -> extractor.extract(fileName, bytes),
                e -> TraceAttrs.of("type", e.type(), "charCount", e.text().length()));
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

        List<TextSegment> segments = tracer.inChild("document.split", RunTypes.CHAIN,
                TraceAttrs.of("charCount", text.length(), "chunkSize", chunkSize, "chunkOverlap", chunkOverlap),
                () -> DocumentSplitters.recursive(chunkSize, chunkOverlap).split(document),
                s -> TraceAttrs.of("segmentCount", s.size()));
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("切块结果为空,无法入库");
        }

        // qwen 嵌入单次 batch 上限 10，分批后再合并。
        // 用一个 embedding 类型的聚合 span 包住：listener 看到当前 span 是 embedding 就只累加 token，不再逐批建子 span
        List<Embedding> embeddings = tracer.inChild("embedding.batch", RunTypes.EMBEDDING,
                TraceAttrs.of("segmentCount", segments.size(), "totalChars", text.length()),
                () -> EmbeddingBatchHelper.embedAll(embeddingModel, segments),
                list -> TraceAttrs.of("count", list.size(), "dimension", list.isEmpty() ? 0 : list.get(0).dimension()));
        int written = tracer.inChild("es.index", RunTypes.TOOL,
                TraceAttrs.of("segmentCount", segments.size()),
                () -> ingestionService.ingestSegments(segments, embeddings, userId),
                n -> TraceAttrs.of("written", n));
        upsertDocument(existing, userId, fileName, text.length(), written);
        return new UploadResult(fileName, extracted.type(), text.length(), written, Instant.now().toEpochMilli());
    }

    /** 文件清单 upsert:同用户同名文件复用同一行(重传覆盖 ES 后刷新统计)。existing 是方法开头已查好的结果,不再重复查询。 */
    private void upsertDocument(Optional<KnowledgeDocument> existing, Long userId, String fileName, long charCount, int chunkCount) {
        KnowledgeDocument doc = existing.orElseGet(() -> new KnowledgeDocument(userId, fileName,
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

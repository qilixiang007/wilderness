package com.wilderness.backend.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.wilderness.backend.ai.ElasticsearchIndexManager;
import com.wilderness.backend.domain.KnowledgeDocument;
import com.wilderness.backend.dto.KnowledgeFileDTO;
import com.wilderness.backend.dto.KnowledgeFilePreviewDTO;
import com.wilderness.backend.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 「我的文件」:列出当前用户上传过的文件、预览分块内容、删除文件。
 * 删除 = 从 ES 按 user_id + file_name 移除对应块,再删清单行。
 */
@Service
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class KnowledgeFileService {

	/** 单次预览最多拉取的分块数,避免超大文件一次性拖回全部内容。 */
	private static final Logger log = LoggerFactory.getLogger(KnowledgeFileService.class);
	private static final int MAX_PREVIEW_CHUNKS = 300;

	private final KnowledgeDocumentRepository repository;
	private final ElasticsearchClient es;
	private final ElasticsearchIndexManager indexManager;

	public KnowledgeFileService(KnowledgeDocumentRepository repository,
			ElasticsearchClient es,
			ElasticsearchIndexManager indexManager) {
		this.repository = repository;
		this.es = es;
		this.indexManager = indexManager;
	}

	public List<KnowledgeFileDTO> list(Long userId) {
		return repository.findByUserIdOrderByUploadedAtDesc(userId).stream()
				.map(d -> new KnowledgeFileDTO(d.getId(), d.getFileName(), d.getType(),
						d.getCharCount(), d.getChunkCount(), d.getUploadedAt()))
				.toList();
	}

	/** 按 chunk_index 顺序取出该文件在知识库中的全部分块文本,供前端分段预览。 */
	public KnowledgeFilePreviewDTO preview(Long id, Long userId) throws Exception {
		KnowledgeDocument doc = repository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在"));
		int size = Math.min(Math.max(doc.getChunkCount(), 1), MAX_PREVIEW_CHUNKS);

		SearchResponse<Map> resp;
		try {
			resp = es.search(s -> s
							.index(indexManager.indexName())
							.query(q -> q.bool(b -> b
									.must(m -> m.term(t -> t.field("user_id").value(String.valueOf(userId))))
									.must(m -> m.term(t -> t.field("file_name").value(doc.getFileName())))))
							.sort(so -> so.field(f -> f.field("chunk_index").order(SortOrder.Asc)))
							.size(size)
							.source(so -> so.filter(f -> f.includes("content"))),
					Map.class);
		} catch (Exception e) {
			log.error("知识库预览检索失败 id={}", id, e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "知识库服务暂时不可用，请稍后再试");
		}

		List<String> chunks = resp.hits().hits().stream()
				.map(h -> h.source())
				.filter(Objects::nonNull)
				.map(src -> String.valueOf(src.get("content")))
				.toList();

		return new KnowledgeFilePreviewDTO(doc.getFileName(), doc.getType(),
				doc.getCharCount(), doc.getChunkCount(), chunks);
	}

	@Transactional
	public void delete(Long id, Long userId) throws Exception {
		KnowledgeDocument doc = repository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在"));
		// 先删 ES(按 user_id + file_name 精确匹配,只删当前用户的同名文件),refresh 立即生效
		try {
			es.deleteByQuery(d -> d.index(indexManager.indexName())
					.refresh(true)
					.query(q -> q.bool(b -> b
							.must(m -> m.term(t -> t.field("user_id").value(String.valueOf(userId))))
							.must(m -> m.term(t -> t.field("file_name").value(doc.getFileName()))))));
		} catch (Exception e) {
			log.error("知识库文件删除失败 id={}", id, e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "知识库服务暂时不可用，请稍后再试");
		}
		repository.delete(doc);
	}
}

package com.wilderness.backend.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.wilderness.backend.ai.ElasticsearchIndexManager;
import com.wilderness.backend.domain.KnowledgeDocument;
import com.wilderness.backend.dto.KnowledgeFileDTO;
import com.wilderness.backend.repository.KnowledgeDocumentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 「我的文件」:列出当前用户上传过的文件、删除文件。
 * 删除 = 从 ES 按 user_id + file_name 移除对应块,再删清单行。
 */
@Service
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class KnowledgeFileService {

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

	@Transactional
	public void delete(Long id, Long userId) throws Exception {
		KnowledgeDocument doc = repository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在"));
		// 先删 ES(按 user_id + file_name 精确匹配,只删当前用户的同名文件),refresh 立即生效
		es.deleteByQuery(d -> d.index(indexManager.indexName())
				.refresh(true)
				.query(q -> q.bool(b -> b
						.must(m -> m.term(t -> t.field("user_id").value(String.valueOf(userId))))
						.must(m -> m.term(t -> t.field("file_name").value(doc.getFileName()))))));
		repository.delete(doc);
	}
}

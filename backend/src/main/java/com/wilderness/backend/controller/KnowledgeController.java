package com.wilderness.backend.controller;

import com.wilderness.backend.ai.KnowledgeUploadService;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.UploadResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge")
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class KnowledgeController {

    private final KnowledgeUploadService uploadService;

    public KnowledgeController(KnowledgeUploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** 用户上传文件入库(Word/PDF/Excel/txt),自动切块向量化后写入知识库。 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResult> upload(@RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(uploadService.upload(file, null));
    }
}

package com.wilderness.backend.controller;

import com.wilderness.backend.ai.KnowledgeUploadService;
import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.KnowledgeFileDTO;
import com.wilderness.backend.dto.UploadResult;
import com.wilderness.backend.service.KnowledgeFileService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class KnowledgeController {

    private final KnowledgeUploadService uploadService;
    private final KnowledgeFileService fileService;

    public KnowledgeController(KnowledgeUploadService uploadService, KnowledgeFileService fileService) {
        this.uploadService = uploadService;
        this.fileService = fileService;
    }

    /** 用户上传文件入库(Word/PDF/Excel/txt),自动切块向量化后写入知识库。仅登录用户可上传。 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResult> upload(@RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(uploadService.upload(file, AuthContext.currentUserId()));
    }

    /** 当前用户上传过的文件清单(仅登录)。 */
    @GetMapping("/files")
    public ApiResponse<List<KnowledgeFileDTO>> files() {
        return ApiResponse.ok(fileService.list(AuthContext.currentUserId()));
    }

    /** 删除当前用户的某个上传文件(ES 块 + 清单行一起删)。 */
    @DeleteMapping("/files/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) throws Exception {
        fileService.delete(id, AuthContext.currentUserId());
        return ApiResponse.ok("已删除", null);
    }
}

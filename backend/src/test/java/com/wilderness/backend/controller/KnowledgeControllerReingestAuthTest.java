package com.wilderness.backend.controller;

import com.wilderness.backend.ai.KnowledgeIngestionService;
import com.wilderness.backend.ai.KnowledgeUploadService;
import com.wilderness.backend.auth.AdminInterceptor;
import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.auth.AuthService;
import com.wilderness.backend.config.GlobalExceptionHandler;
import com.wilderness.backend.service.KnowledgeFileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/knowledge/reingest 会先清空再重建公共语料,并把全部语料重新向量化,只允许管理员调用。
 * KnowledgeController 仅在配置了 DashScope key 时注册,test profile 下不存在,无法走 @SpringBootTest;
 * 这里用 standalone MockMvc 挂真实的控制器 + 真实的 AdminInterceptor + 全局异常处理,只 mock 下游服务。
 * 线上登录态由 AuthInterceptor 写入 AuthContext,standalone MockMvc 在测试线程同步执行,直接写入即可。
 */
class KnowledgeControllerReingestAuthTest {

    private static final long ADMIN_ID = 1L;
    private static final long USER_ID = 2L;

    private KnowledgeIngestionService ingestionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ingestionService = mock(KnowledgeIngestionService.class);
        AuthService authService = mock(AuthService.class);
        when(authService.isAdminUser(ADMIN_ID)).thenReturn(true);
        when(authService.isAdminUser(USER_ID)).thenReturn(false);

        KnowledgeController controller = new KnowledgeController(
                mock(KnowledgeUploadService.class), mock(KnowledgeFileService.class), ingestionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new AdminInterceptor(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void 普通用户调用返回403且不触碰公共语料() throws Exception {
        AuthContext.setUserId(USER_ID);

        mockMvc.perform(post("/api/knowledge/reingest"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("需要管理员权限"));

        verifyNoInteractions(ingestionService);
    }

    @Test
    void 未登录调用返回401且不触碰公共语料() throws Exception {
        mockMvc.perform(post("/api/knowledge/reingest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(ingestionService);
    }

    @Test
    void 管理员调用先清空再重新入库() throws Exception {
        AuthContext.setUserId(ADMIN_ID);
        when(ingestionService.ingestFromClasspath()).thenReturn(42);

        mockMvc.perform(post("/api/knowledge/reingest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(42));

        InOrder order = inOrder(ingestionService);
        order.verify(ingestionService).deletePublicCorpus();
        order.verify(ingestionService).ingestFromClasspath();
    }
}

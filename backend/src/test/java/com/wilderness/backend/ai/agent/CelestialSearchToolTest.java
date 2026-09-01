package com.wilderness.backend.ai.agent;

import com.wilderness.backend.ai.HybridContentRetriever;
import com.wilderness.backend.dto.AiSource;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 天体生成检索工具的纯单元测试：mock 掉 ES 混合检索器，
 * 验证 @Tool 命中后 steps/sources 记录、空结果与异常降级、同天体去重。
 * 不依赖 Spring 上下文 / Redis / ES，直接跑 JUnit 即可。
 */
class CelestialSearchToolTest {

    private HybridContentRetriever retriever;
    private CelestialSearchTool tool;

    @BeforeEach
    void setUp() {
        retriever = mock(HybridContentRetriever.class);
        tool = new CelestialSearchTool(retriever);
    }

    private Content content(String slug, String zhName, String type, String text) {
        return Content.from(TextSegment.from(text, new Metadata()
                .put("slug", slug)
                .put("zh_name", zhName)
                .put("type", type)));
    }

    @Test
    void 命中时返回资料并记录轨迹与来源() {
        when(retriever.retrieve(any(Query.class), isNull())).thenReturn(List.of(
                content("betelgeuse", "参宿四", "star",
                        "参宿四是一颗红超巨星，半径约为太阳的 900 倍，表面温度约 3600K。")));

        String result = tool.searchCelestial("恒星", "红色 巨大");

        assertTrue(result.contains("参宿四"));
        assertTrue(result.contains("红超巨星"));
        assertTrue(tool.steps().stream().anyMatch(s -> s.phase().equals("知识库检索")));
        assertTrue(tool.steps().stream().anyMatch(s -> s.phase().equals("参考锚点")));
        assertEquals(1, tool.sources().size());
        AiSource src = tool.sources().get(0);
        assertEquals("参宿四", src.title());
        assertEquals("betelgeuse", src.slug());
        assertEquals("star", src.type());
    }

    @Test
    void 空结果时返回降级提示并记录未命中() {
        when(retriever.retrieve(any(Query.class), isNull())).thenReturn(List.of());

        String result = tool.searchCelestial("恒星", "不存在的东西");

        assertTrue(result.contains("未检索到"));
        assertTrue(tool.steps().stream().anyMatch(s -> s.detail().contains("未命中")));
        assertTrue(tool.sources().isEmpty());
    }

    @Test
    void 检索异常时降级为常识生成不打断() {
        when(retriever.retrieve(any(Query.class), isNull()))
                .thenThrow(new IllegalStateException("ES 连接超时"));

        String result = tool.searchCelestial("恒星", "红色");

        assertTrue(result.contains("检索失败"));
        assertTrue(result.contains("基于常识生成"));
        assertTrue(tool.steps().stream().anyMatch(s -> s.phase().equals("检索异常")));
    }

    @Test
    void 同一天体多次命中只保留一个来源() {
        when(retriever.retrieve(any(Query.class), isNull())).thenReturn(List.of(
                content("betelgeuse", "参宿四", "star", "第一段"),
                content("betelgeuse", "参宿四", "star", "第二段")));

        tool.searchCelestial("恒星", "红色");

        assertEquals(1, tool.sources().size());
        // 返回给 LLM 的文本仍包含两段，但来源展示只去重保留一个
        assertTrue(tool.steps().stream().anyMatch(s -> s.detail().contains("参宿四")));
    }

    @Test
    void 空参数不抛异常() {
        when(retriever.retrieve(any(Query.class), isNull())).thenReturn(List.of());

        String result = tool.searchCelestial(null, null);

        // 参数为 null 时查询词为空串，工具应直接降级提示，且不调用检索
        assertTrue(result.contains("未提供有效的检索关键词"));
        assertTrue(tool.steps().stream().anyMatch(s -> s.detail().contains("未提供检索关键词")));
    }
}

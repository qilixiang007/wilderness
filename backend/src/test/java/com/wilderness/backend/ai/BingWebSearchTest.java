package com.wilderness.backend.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 联网检索集成测试(需能访问 cn.bing.com,不含 LLM)。
 * 验证必应搜索结果页可抓取、jsoup 选择器能解析出标题/链接/摘要。
 */
class BingWebSearchTest {

    private final BingWebSearch search = new BingWebSearch();

    @Test
    void searchReturnsRealResults() throws Exception {
        List<WebResult> results = search.search("木星是太阳系最大的行星", 3);
        assertFalse(results.isEmpty());
        WebResult r = results.get(0);
        assertNotNull(r.title());
        assertTrue(r.url().startsWith("http"));
        assertNotNull(r.snippet());
    }
}

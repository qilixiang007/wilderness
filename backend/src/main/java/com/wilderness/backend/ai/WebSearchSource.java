package com.wilderness.backend.ai;

import java.util.List;

/**
 * 可插拔的联网检索源抽象。
 * 默认提供维基百科实现(公共 API,免费、无需 key);面试可扩展为 Bing / Serper 等商业搜索 API。
 */
public interface WebSearchSource {

    /** 检索源名称,用于前端标注来源。 */
    String name();

    /** 对 query 检索,返回至多 limit 条结果。 */
    List<WebResult> search(String query, int limit) throws Exception;
}

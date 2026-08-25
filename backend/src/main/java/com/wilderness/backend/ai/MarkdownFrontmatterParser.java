package com.wilderness.backend.ai;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析带 YAML frontmatter 的 Markdown 语料文件。
 * 语料格式:
 * <pre>
 * ---
 * slug: xxx
 * type: star
 * zhName: 中文名
 * enName: English name
 * ---
 *
 * # 标题
 * ...
 * </pre>
 */
@Component
public class MarkdownFrontmatterParser {

    public record ParsedDocument(String slug, String type, String zhName, String enName, String content) {
    }

    public ParsedDocument parse(String markdown) {
        if (markdown == null || !markdown.startsWith("---")) {
            throw new IllegalArgumentException("Markdown 缺少 YAML frontmatter");
        }
        int end = markdown.indexOf("\n---", 3);
        if (end < 0) {
            throw new IllegalArgumentException("Markdown frontmatter 格式不完整");
        }
        String frontmatter = markdown.substring(3, end).trim();
        String content = markdown.substring(end + 4).trim();

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = new Yaml().load(frontmatter);
        if (meta == null) {
            meta = new LinkedHashMap<>();
        }

        return new ParsedDocument(
                str(meta.get("slug")),
                str(meta.get("type")),
                str(meta.get("zhName")),
                str(meta.get("enName")),
                content);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}

package com.wilderness.backend.ai.trace;

import java.util.LinkedHashMap;
import java.util.Map;

/** 构造 span 的 inputs/outputs：保持插入顺序、允许 null 值（Map.of 遇到 null 会直接 NPE）。 */
public final class TraceAttrs {

    private TraceAttrs() {
    }

    public static Map<String, Object> of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues 必须成对出现");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}

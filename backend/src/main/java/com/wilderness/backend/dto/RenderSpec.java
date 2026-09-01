package com.wilderness.backend.dto;

/**
 * 前端 SVG 程序化渲染天体的参数（由 LLM 结构化输出产生）。
 *
 * 所有字段均可空：LLM 输出的 JSON 可能缺项/非法，前端用默认值合并 + 数值 clamp 容错。
 * category 为六类英文枚举，与知识库 type 对应，前端据此选择渲染形态：
 * star / planet / moon / galaxy / nebula / small-bodies。
 * 颜色一律用 #RRGGBB 十六进制。
 */
public record RenderSpec(
        String category,        // star | planet | moon | galaxy | nebula | small-bodies
        String primaryColor,    // 主色
        String secondaryColor,  // 辅色（渐变/细节）
        String accentColor,     // 点缀色（环/亮斑/彗尾）
        Double coreSize,        // 中心体大小 0.2~0.9
        Double brightness,      // 亮度 0~1（光晕强度）
        Boolean hasRings,       // 行星环
        Double ringTilt,        // 环倾角（度）
        Integer bands,          // 云带数量（恒星/行星大气）
        Double bandContrast,    // 云带对比度 0~1
        Integer spots,          // 斑点/亮区数量（大红斑、月海）
        Double surfaceTexture,  // 表面纹理密度（环形山/斑块）0~1
        Double noise,           // 噪点/尘埃密度（星云、星系）0~1
        Integer spiralArms,     // 星系旋臂数量
        Double spiralTwist,     // 旋臂缠绕度 0~1
        Double tailLength,      // 彗尾长度 0~1
        Double tailSpread,      // 彗尾散开度 0~1
        Double glowSpread       // 光晕扩散半径 0~1
) {
}

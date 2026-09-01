package com.wilderness.backend.ai.image;

/**
 * 文生图扩展接口（天体生成 Agent 的图片升级通道）。
 *
 * 未配置对应实现时，CelestialAgentService 通过 ObjectProvider 拿到 null，
 * 图片走默认的 SVG 程序化渲染通道，本接口不影响主流程。
 */
public interface ImageGenerator {

    /** 生成图片并返回可访问的 URL；失败抛异常（由上层捕获后降级为 SVG）。 */
    String generate(String prompt) throws Exception;
}

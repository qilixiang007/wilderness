package com.wilderness.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 应用启动后自动入库。ES 未就绪或网络异常时不阻塞应用启动,仅记录告警。
 */
@Component
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class KnowledgeIngestionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionRunner.class);

    private final KnowledgeIngestionService ingestionService;

    public KnowledgeIngestionRunner(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(String... args) {
        try {
            ingestionService.ingestFromClasspath();
        } catch (Exception e) {
            log.error("知识库入库失败(可稍后手动重跑):{}", e.getMessage());
        }
    }
}

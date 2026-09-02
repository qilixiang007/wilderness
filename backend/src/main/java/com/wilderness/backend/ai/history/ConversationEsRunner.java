package com.wilderness.backend.ai.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动后确保对话分析索引存在。ES 未就绪时仅告警，不阻塞启动。
 */
@Component
public class ConversationEsRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConversationEsRunner.class);

    private final ConversationEsIndexManager indexManager;

    public ConversationEsRunner(ConversationEsIndexManager indexManager) {
        this.indexManager = indexManager;
    }

    @Override
    public void run(String... args) {
        try {
            indexManager.ensureIndex();
        } catch (Exception e) {
            log.error("对话历史索引初始化失败(可稍后重试): {}", e.getMessage());
        }
    }
}

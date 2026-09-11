package com.wilderness.backend.config;

import com.wilderness.backend.ai.trace.TracingTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 相关的受管线程池。两个池形状不同是有意的:
 * - aiCompareExecutor: 编排任务可以排队等待,慢一点也不丢活;
 * - aiStreamExecutor : SSE 长连接一旦排队阻塞就等于拖住整条流式响应,满了必须直接拒绝。
 */
@Configuration
public class AiExecutorConfig {

    @Bean
    public ThreadPoolTaskExecutor aiCompareExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // core=max=4：Java 线程池的调度规则是 core 用满后新任务优先进队列排队，
        // 只有队列也满了才会继续开线程到 max——core 若小于前端 COMPARE_MAX(4)，
        // 4 个天体同时对比时后两个会排队等前两个的线程释放，实际上没有真正并行。
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("ai-cmp-");
        // 透传当前 span：并行的 explain 挂到对比 trace 下
        executor.setTaskDecorator(new TracingTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor aiStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(0);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("ai-sse-");
        executor.setTaskDecorator(new TracingTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

package com.wilderness.backend.ai.trace.store;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 有界内存写入队列 + 单后台线程批量冲刷，把落库从业务线程上彻底剥离。
 *
 * 背压设计：
 * - 双重上限：条数（ArrayBlockingQueue 容量）+ 字节预算。输入输出按约定不截断，单条 trace 可能很大，
 *   只限条数的话内存峰值不可控；
 * - 满了直接丢弃并计数（ai.trace.dropped），绝不阻塞业务线程——追踪数据可以丢，用户请求不能慢；
 * - 冲刷时机：攒满 batchSize 条，或第一条入队后等满 flushInterval，二者先到为准。
 *
 * 实现 SmartLifecycle：应用关闭时先停线程并把剩余数据写完，再轮到数据源等 Bean 销毁。
 */
@Component
public class TraceWriteQueue implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TraceWriteQueue.class);

    private final BlockingQueue<TraceSnapshot> queue;
    private final AtomicLong queuedBytes = new AtomicLong();
    private final long maxBytes;
    private final int batchSize;
    private final long flushIntervalMs;
    private final TraceBatchWriter writer;
    private final Counter droppedByCapacity;
    private final Counter droppedByBytes;

    private volatile boolean running;
    private Thread worker;

    public TraceWriteQueue(TraceBatchWriter writer,
                           MeterRegistry registry,
                           @Value("${wilderness.trace.store.queue-capacity:2000}") int capacity,
                           @Value("${wilderness.trace.store.queue-max-bytes:67108864}") long maxBytes,
                           @Value("${wilderness.trace.store.batch-size:100}") int batchSize,
                           @Value("${wilderness.trace.store.flush-interval-ms:500}") long flushIntervalMs) {
        this.writer = writer;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.maxBytes = maxBytes;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.droppedByCapacity = Counter.builder("ai.trace.dropped").tag("reason", "capacity")
                .description("写入队列已满而丢弃的 trace 数").register(registry);
        this.droppedByBytes = Counter.builder("ai.trace.dropped").tag("reason", "bytes")
                .description("超出字节预算而丢弃的 trace 数").register(registry);
        Gauge.builder("ai.trace.queue.size", queue, BlockingQueue::size)
                .description("写入队列中待落库的 trace 数").register(registry);
        Gauge.builder("ai.trace.queue.bytes", queuedBytes, AtomicLong::get)
                .description("写入队列估算占用字节").register(registry);
    }

    /** 非阻塞入队；超出条数或字节上限时丢弃并返回 false。 */
    public boolean offer(TraceSnapshot snapshot) {
        long size = snapshot.approxBytes();
        long current;
        do {
            current = queuedBytes.get();
            if (current + size > maxBytes) {
                droppedByBytes.increment();
                log.warn("trace 写入队列字节预算已满，丢弃 traceId={}", snapshot.traceId());
                return false;
            }
        } while (!queuedBytes.compareAndSet(current, current + size));

        if (!queue.offer(snapshot)) {
            queuedBytes.addAndGet(-size);
            droppedByCapacity.increment();
            log.warn("trace 写入队列已满，丢弃 traceId={}", snapshot.traceId());
            return false;
        }
        return true;
    }

    private void runLoop() {
        List<TraceSnapshot> batch = new ArrayList<>(batchSize);
        while (running) {
            try {
                TraceSnapshot first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                long deadline = System.currentTimeMillis() + flushIntervalMs;
                while (batch.size() < batchSize) {
                    long wait = deadline - System.currentTimeMillis();
                    if (wait <= 0) {
                        break;
                    }
                    TraceSnapshot next = queue.poll(wait, TimeUnit.MILLISECONDS);
                    if (next == null) {
                        break;
                    }
                    batch.add(next);
                }
                flush(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("trace 写入线程异常（继续运行）: {}", e.getMessage());
                batch.clear();
            }
        }
        flush(batch);
    }

    private void flush(List<TraceSnapshot> batch) {
        if (batch.isEmpty()) {
            return;
        }
        for (TraceSnapshot snapshot : batch) {
            queuedBytes.addAndGet(-snapshot.approxBytes());
        }
        writer.write(List.copyOf(batch));
        batch.clear();
    }

    @Override
    public void start() {
        running = true;
        worker = new Thread(this::runLoop, "trace-writer");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            // 不 interrupt：写库中途被中断可能留下半截事务；poll 最多 1 秒就会返回并看到 running=false
            try {
                worker.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 线程退出后把队列里剩下的一次性写完（尽力而为）
        List<TraceSnapshot> rest = new ArrayList<>();
        queue.drainTo(rest);
        flush(rest);
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

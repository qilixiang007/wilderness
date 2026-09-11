package com.wilderness.backend.ai.trace;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 把每次 LLM 调用记成当前 span 下的 llm 子 span，附带 token 用量与模型名。
 *
 * 为什么流式也能挂对父节点（已核对 langchain4j 1.19 字节码）：
 * - StreamingChatModel.chat 在调用线程上同步触发 onRequest，此时业务代码设置的当前 span 仍有效；
 * - onRequest 与 onResponse 共用同一个 attributes Map，span 放进去即可在回调线程取回，不依赖 ThreadLocal。
 */
@Component
public class TracingChatModelListener implements ChatModelListener {

    private static final String SPAN_KEY = TracingChatModelListener.class.getName() + ".span";

    private final Tracer tracer;

    public TracingChatModelListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        String model = ctx.chatRequest().modelName();
        Span span = tracer.child(model == null ? "chat_model" : model, RunTypes.LLM,
                TraceAttrs.of("messages", toMessages(ctx.chatRequest().messages())));
        if (!span.isNoop()) {
            ctx.attributes().put(SPAN_KEY, span);
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        if (!(ctx.attributes().get(SPAN_KEY) instanceof Span span)) {
            return;
        }
        ChatResponse response = ctx.chatResponse();
        TokenUsage usage = response.tokenUsage();
        if (usage != null) {
            span.setUsage(usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount());
        }
        span.setModelName(response.modelName());
        AiMessage message = response.aiMessage();
        span.end(TraceAttrs.of(
                "content", message == null ? null : message.text(),
                "finishReason", response.finishReason() == null ? null : response.finishReason().name()));
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        if (ctx.attributes().get(SPAN_KEY) instanceof Span span) {
            span.fail(ctx.error());
        }
    }

    /** 转成 LangSmith llm run 惯用的 messages 数组：[{role, content}]。 */
    private List<Map<String, Object>> toMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(m -> TraceAttrs.of("role", m.type().name().toLowerCase(), "content", textOf(m)))
                .toList();
    }

    private String textOf(ChatMessage message) {
        if (message instanceof SystemMessage system) {
            return system.text();
        }
        if (message instanceof UserMessage user) {
            return user.hasSingleText() ? user.singleText() : String.valueOf(user.contents());
        }
        if (message instanceof AiMessage ai) {
            return ai.text();
        }
        return String.valueOf(message);
    }
}

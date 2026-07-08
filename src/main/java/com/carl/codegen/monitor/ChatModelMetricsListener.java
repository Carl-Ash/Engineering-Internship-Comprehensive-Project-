package com.carl.codegen.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * ChatModel 指标监听器 — 在 AI 调用的关键生命周期节点触发指标收集。
 */
@Component
@Slf4j
public class ChatModelMetricsListener implements ChatModelListener {

    // attributes 中储存请求开始 Instant 的 key
    private static final String REQUEST_START_KEY = "request_start_instant";

    // attributes 中储存 MonitorContext 的 key
    private static final String MONITOR_CONTEXT_KEY = "monitor_context";

    // 注入指标收集器
    @Resource
    private AiModelMetricsCollector metricsCollector;

    /**
     * 请求发出前触发 —— 记录开始时间、快照当前线程的监控上下文。
     */
    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 快照开始时间，用于 onResponse / onError 时计算耗时
        requestContext.attributes().put(REQUEST_START_KEY, Instant.now());

        // 从 ThreadLocal 取出业务上下文，存入 attributes。因为 onResponse / onError 可能在其他线程执行
        MonitorContext context = MonitorContextHolder.getContext();
        requestContext.attributes().put(MONITOR_CONTEXT_KEY, context);

        String userId = context.getUserId();
        String appId = context.getAppId();
        String modelName = requestContext.chatRequest().modelName();

        metricsCollector.incrementRequestCount(userId, appId, modelName, "started");
    }

    /**
     * 收到完整响应后触发 —— 记录成功、延迟和 Token 消耗。
     */
    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Map<Object, Object> attributes = responseContext.attributes();
        // 从 attributes 取回 onRequest 时存入的上下文（可能跨线程）
        MonitorContext context = (MonitorContext) attributes.get(MONITOR_CONTEXT_KEY);

        String userId = context.getUserId();
        String appId = context.getAppId();
        String modelName = responseContext.chatResponse().modelName();

        // 记录请求成功，响应时间，Token 消耗
        metricsCollector.incrementRequestCount(userId, appId, modelName, "success");
        recordLatency(attributes, userId, appId, modelName);
        recordTokenCounts(responseContext, userId, appId, modelName);
    }

    /**
     * 请求失败时触发 —— 记录错误、错误类型，并仍然统计延迟（用于分析错误请求特征）。
     */
    @Override
    public void onError(ChatModelErrorContext errorContext) {
        // 从监控上下文获取用户 ID、应用 ID、模型名称（可能跨线程）
        MonitorContext context = MonitorContextHolder.getContext();
        String userId = context.getUserId();
        String appId = context.getAppId();
        String modelName = errorContext.chatRequest().modelName();

        // 拿到错误消息的简短描述，避免带堆栈的完整信息进入标签
        String errorMessage = errorContext.error().getMessage();

        // 记录请求失败
        metricsCollector.incrementRequestCount(userId, appId, modelName, "error");
        metricsCollector.incrementErrorCount(userId, appId, modelName, errorMessage);

        // 失败的请求同样记录耗时，便于分析超时等性能问题
        Map<Object, Object> attributes = errorContext.attributes();
        recordLatency(attributes, userId, appId, modelName);
    }

    /**
     * 计算并记录本次 AI 调用延迟。
     */
    private void recordLatency(Map<Object, Object> attributes, String userId, String appId, String modelName) {
        Instant start = (Instant) attributes.get(REQUEST_START_KEY);
        if (start != null) {
            Duration latency = Duration.between(start, Instant.now());
            metricsCollector.recordLatency(userId, appId, modelName, latency);
        }
    }

    /**
     * 从响应中提取 Token 使用量并逐类累加。
     */
    private void recordTokenCounts(ChatModelResponseContext responseContext, String userId, String appId, String modelName) {
        TokenUsage tokenUsage = responseContext.chatResponse().tokenUsage();
        if (tokenUsage != null) {
            metricsCollector.addTokenCount(userId, appId, modelName, "input", tokenUsage.inputTokenCount());
            metricsCollector.addTokenCount(userId, appId, modelName, "output", tokenUsage.outputTokenCount());
            metricsCollector.addTokenCount(userId, appId, modelName, "total", tokenUsage.totalTokenCount());
        }
    }
}

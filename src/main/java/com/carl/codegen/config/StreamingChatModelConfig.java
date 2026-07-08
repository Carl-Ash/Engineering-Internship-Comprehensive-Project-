package com.carl.codegen.config;

import com.carl.codegen.monitor.ChatModelMetricsListener;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;

/**
 * 通用流式聊天模型多例配置 — 每次获取创建新实例，解决并发阻塞问题。
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@Data
public class StreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private boolean logRequests;

    private boolean logResponses;

    /** 指标监听器，在 AI 调用各阶段触发指标收集 */
    @Resource
    private ChatModelMetricsListener chatModelMetricsListener;

    @Bean
    @Scope("prototype")
    public StreamingChatModel generalStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                // 注册监听器：每个模型实例独立持有，线程安全
                .listeners(List.of(chatModelMetricsListener))
                .build();
    }
}

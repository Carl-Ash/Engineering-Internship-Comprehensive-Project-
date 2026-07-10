package com.carl.codegen.core.handler;

import com.carl.codegen.model.enums.ChatHistoryMessageTypeEnum;
import com.carl.codegen.model.entity.User;
import com.carl.codegen.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 透传流式文本，仅收集完整响应用于持久化
 */
@Slf4j
public class SimpleTextStreamHandler {

    public Flux<String> handle(Flux<String> flux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StringBuilder responseCollector = new StringBuilder();
        return flux
                .map(chunk -> {
                    // 不收集心跳和状态消息，只收集实际AI内容
                    if (!"\n__hb__".equals(chunk) && !chunk.startsWith("正在生成") && !chunk.contains("生成失败")) {
                        responseCollector.append(chunk);
                    }
                    return chunk;
                })
                .doOnComplete(() -> {
                    String aiResponse = responseCollector.toString().trim();
                    if (!aiResponse.isEmpty()) {
                        chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    }
                })
                .doOnError(error -> {
                    // 失败时不保存错误信息到对话历史，避免污染上下文
                    log.warn("流处理出错，不保存到对话历史: {}", error.getMessage());
                });
    }
}

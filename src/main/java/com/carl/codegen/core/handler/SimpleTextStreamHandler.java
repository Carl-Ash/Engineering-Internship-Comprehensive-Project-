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
                    responseCollector.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    String aiResponse = responseCollector.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }
}

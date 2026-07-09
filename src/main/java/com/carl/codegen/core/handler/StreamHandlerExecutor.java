package com.carl.codegen.core.handler;

import com.carl.codegen.model.entity.User;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import com.carl.codegen.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 按代码生成类型路由到对应的流处理器
 */
@Slf4j
@Component
public class StreamHandlerExecutor {

    public Flux<String> execute(Flux<String> flux,
                                ChatHistoryService chatHistoryService,
                                long appId, User loginUser, CodeGenTypeEnum codeGenType) {
        return switch (codeGenType) {
            case HTML, MULTI_FILE -> new SimpleTextStreamHandler().handle(flux, chatHistoryService, appId, loginUser);
            // VUE3 now uses non-streaming ChatModel, no JSON message wrapping needed
            case VUE3 -> new SimpleTextStreamHandler().handle(flux, chatHistoryService, appId, loginUser);
        };
    }
}

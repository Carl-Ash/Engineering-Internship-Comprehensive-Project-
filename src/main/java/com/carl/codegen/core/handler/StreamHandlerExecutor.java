package com.carl.codegen.core.handler;

import com.carl.codegen.model.entity.User;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import com.carl.codegen.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 按代码生成类型路由到对应的流处理器
 */
@Slf4j
@Component
public class StreamHandlerExecutor {

    @Resource
    private JsonMessageStreamHandler jsonMessageStreamHandler;

    public Flux<String> execute(Flux<String> flux,
                                ChatHistoryService chatHistoryService,
                                long appId, User loginUser, CodeGenTypeEnum codeGenType) {
        return switch (codeGenType) {
            case VUE3 -> jsonMessageStreamHandler.handle(flux, chatHistoryService, appId, loginUser);
            case HTML, MULTI_FILE -> new SimpleTextStreamHandler().handle(flux, chatHistoryService, appId, loginUser);
        };
    }
}

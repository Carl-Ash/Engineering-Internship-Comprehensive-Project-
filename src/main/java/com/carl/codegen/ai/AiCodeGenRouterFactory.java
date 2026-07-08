package com.carl.codegen.ai;

import com.carl.codegen.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI代码生成类型路由器工厂 — 每次调用创建独立的路由服务实例，解决并发阻塞问题。
 *
 */
@Slf4j
@Configuration
public class AiCodeGenRouterFactory {

    /**
     * 创建 AI 代码生成类型路由服务实例（多例模式）
     */
    public AiCodeGenRouter createAiCodeGenRouter() {
        ChatModel routingChatModel = SpringContextUtil.getBean("routingChatModel", ChatModel.class);
        return AiServices.builder(AiCodeGenRouter.class)
                .chatModel(routingChatModel)
                .build();
    }

    /**
     * 默认 Bean，保证兼容
     */
    @Bean
    public AiCodeGenRouter aiCodeGenRouter() {
        return createAiCodeGenRouter();
    }
}

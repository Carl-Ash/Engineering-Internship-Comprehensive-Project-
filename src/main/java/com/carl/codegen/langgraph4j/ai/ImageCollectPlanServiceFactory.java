package com.carl.codegen.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片收集规划服务工厂 — 注入 ChatModel，利用结构化输出生成收集计划
 */
@Configuration
public class ImageCollectPlanServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Bean
    public ImageCollectPlanService imageCollectPlanService() {
        return AiServices.builder(ImageCollectPlanService.class)
                .chatModel(chatModel)
                .build();
    }
}

package com.carl.codegen.langgraph4j.ai;

import com.carl.codegen.langgraph4j.tools.ImageSearchTool;
import com.carl.codegen.langgraph4j.tools.LogoGeneratorTool;
import com.carl.codegen.langgraph4j.tools.MermaidDiagramTool;
import com.carl.codegen.langgraph4j.tools.UndrawIllustrationTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片收集 AI 服务工厂 — 注入 ChatModel 和图片相关工具。
 */
@Configuration
public class ImageCollectServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private ImageSearchTool imageSearchTool;

    @Resource
    private UndrawIllustrationTool undrawIllustrationTool;

    @Resource
    private MermaidDiagramTool mermaidDiagramTool;

    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    @Bean
    public ImageCollectService imageCollectService() {
        return AiServices.builder(ImageCollectService.class)
                .chatModel(chatModel)
                .tools(imageSearchTool, undrawIllustrationTool, mermaidDiagramTool, logoGeneratorTool)
                .build();
    }
}

package com.carl.codegen.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface AiCodeGenService {

    /**
     * 生成HTML代码
     * @param prompt 代码提示
     * @return 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    String generateHtmlCode(@MemoryId Long appId, @UserMessage String prompt);

    /**
     * 生成多文件代码
     * @param prompt 代码提示
     * @return AI 最终回复文本
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    String generateMultiFileCode(@MemoryId Long appId, @UserMessage String prompt);

    /**
     * 生成HTML代码
     * @param prompt 代码提示
     * @return 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStreaming(String prompt);

    /**
     * 生成多文件代码
     * @param prompt 代码提示
     * @return 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStreaming(String prompt);

    /**
     * 生成 Vue3 项目代码 (流式输出)
     * @param prompt 代码提示
     * @return 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-vue3-system-prompt.txt")
    TokenStream generateVue3CodeStreaming(@MemoryId Long appId, @UserMessage String prompt);

    /**
     * 生成 Vue3 项目代码 (非流式，使用 ChatModel 避免 DeepSeek 流式工具调用解析问题)
     * @param prompt 代码提示
     * @return AI 最终回复文本
     */
    @SystemMessage(fromResource = "prompt/codegen-vue3-system-prompt.txt")
    String generateVue3Code(@MemoryId Long appId, @UserMessage String prompt);
}

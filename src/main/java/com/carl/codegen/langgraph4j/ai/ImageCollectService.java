package com.carl.codegen.langgraph4j.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 图片收集 AI 服务 — 根据用户需求自主调用工具收集图片资源。
 */
public interface ImageCollectService {

    @SystemMessage(fromResource = "prompt/image-collect-system-prompt.txt")
    String collectImages(@UserMessage String userPrompt);
}

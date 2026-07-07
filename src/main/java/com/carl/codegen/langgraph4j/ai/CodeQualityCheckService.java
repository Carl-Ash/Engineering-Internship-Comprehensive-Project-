package com.carl.codegen.langgraph4j.ai;

import com.carl.codegen.langgraph4j.state.QualityResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 代码质量检查 AI 服务 — 分析代码并返回检查结果。
 */
public interface CodeQualityCheckService {

    /**
     * 检查代码质量，返回质检结果
     */
    @SystemMessage(fromResource = "prompt/code-quality-check-system-prompt.txt")
    QualityResult checkCodeQuality(@UserMessage String codeContent);
}

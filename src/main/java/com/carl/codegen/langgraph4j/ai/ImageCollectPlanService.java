package com.carl.codegen.langgraph4j.ai;

import com.carl.codegen.langgraph4j.state.ImageCollectPlan;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 图片收集规划 AI 服务 — 分析用户需求，返回结构化收集计划
 */
public interface ImageCollectPlanService {

    @SystemMessage(fromResource = "prompt/image-collect-plan-system-prompt.txt")
    ImageCollectPlan planImageCollection(@UserMessage String userPrompt);
}

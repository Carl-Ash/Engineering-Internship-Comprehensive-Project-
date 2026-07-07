package com.carl.codegen.langgraph4j.node;

import com.carl.codegen.constant.AppConstant;
import com.carl.codegen.core.AiCodeGenFacade;
import com.carl.codegen.langgraph4j.state.QualityResult;
import com.carl.codegen.langgraph4j.state.WorkflowContext;
import com.carl.codegen.utils.SpringContextUtil;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码生成节点 — 调用 AI 生成代码并等待流式输出完成。
 */
@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.fromState(state);
            log.info("执行节点: 代码生成");

            String userMessage = buildUserMessage(context);
            CodeGenTypeEnum codeGenType = context.getCodeGenType();
            // 流式生成并同步等待完成
            AiCodeGenFacade facade = SpringContextUtil.getBean(AiCodeGenFacade.class);
            log.info("开始生成代码，类型: {} ({})", codeGenType.getValue(), codeGenType.getText());

            Long appId = 0L;
            Flux<String> codeStream = facade.genAndSaveStream(userMessage, codeGenType, appId);
            codeStream.blockLast(Duration.ofMinutes(10));

            String codeOutputDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, codeGenType.getValue(), appId);
            log.info("代码生成完成，目录: {}", codeOutputDir);

            // 更新状态
            context.setCurrentStep("代码生成");
            context.setCodeOutputDir(codeOutputDir);
            return WorkflowContext.toStateMap(context);
        });
    }

    /**
     * 构造用户消息，存在质检失败时替换为错误修复提示词
     */
    private static String buildUserMessage(WorkflowContext context) {
        String userMessage = context.getEnhancedPrompt();
        QualityResult qualityResult = context.getQualityResult();
        // 质检失败则构造修复提示词
        if (isQualityCheckFailed(qualityResult)) {
            userMessage = buildErrorFixPrompt(qualityResult);
        }
        return userMessage;
    }

    /**
     * 判断质检是否失败
     */
    private static boolean isQualityCheckFailed(QualityResult qualityResult) {
        return qualityResult != null
                && !qualityResult.getIsValid()
                && qualityResult.getErrors() != null
                && !qualityResult.getErrors().isEmpty();
    }

    /**
     * 构造错误修复提示词
     */
    private static String buildErrorFixPrompt(QualityResult qualityResult) {
        StringBuilder errorInfo = new StringBuilder();
        errorInfo.append("\n\n## 上次生成的代码存在以下问题，请修复：\n");
        // 拼接错误列表
        qualityResult.getErrors().forEach(error ->
                errorInfo.append("- ").append(error).append("\n"));
        // 拼接修复建议（如果有）
        if (qualityResult.getSuggestions() != null && !qualityResult.getSuggestions().isEmpty()) {
            errorInfo.append("\n## 修复建议：\n");
            qualityResult.getSuggestions().forEach(suggestion ->
                    errorInfo.append("- ").append(suggestion).append("\n"));
        }
        errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。");
        return errorInfo.toString();
    }
}

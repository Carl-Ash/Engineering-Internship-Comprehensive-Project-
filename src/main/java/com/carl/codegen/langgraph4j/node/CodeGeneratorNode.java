package com.carl.codegen.langgraph4j.node;

import com.carl.codegen.constant.AppConstant;
import com.carl.codegen.core.AiCodeGenFacade;
import com.carl.codegen.langgraph4j.state.WorkflowContext;
import com.carl.codegen.utils.SpringContextUtil;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;

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

            String userMessage = context.getEnhancedPrompt();
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
}

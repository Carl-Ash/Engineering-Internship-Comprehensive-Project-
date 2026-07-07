package com.carl.codegen.langgraph4j.node;

import com.carl.codegen.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码生成节点 — 根据增强后的 Prompt 生成代码。
 */
@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.fromState(state);
            log.info("执行节点: 代码生成");

            // TODO: 实际执行代码生成逻辑
            String codeOutputDir = "/tmp/generated/fake-code";

            context.setCurrentStep("代码生成");
            context.setCodeOutputDir(codeOutputDir);
            log.info("代码生成完成，目录: {}", codeOutputDir);
            return WorkflowContext.toStateMap(context);
        });
    }
}

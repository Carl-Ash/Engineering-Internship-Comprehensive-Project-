package com.carl.codegen.langgraph4j.node;

import com.carl.codegen.langgraph4j.state.WorkflowContext;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 智能路由节点 — 根据 Prompt 选择合适的代码生成类型。
 */
@Slf4j
public class RouterNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.fromState(state);
            log.info("执行节点: 智能路由");

            // TODO: 实际执行智能路由逻辑
            CodeGenTypeEnum codeGenType = CodeGenTypeEnum.HTML;

            context.setCurrentStep("智能路由");
            context.setCodeGenType(codeGenType);
            log.info("路由决策完成，选择类型: {}", codeGenType.getValue());
            return WorkflowContext.toStateMap(context);
        });
    }
}

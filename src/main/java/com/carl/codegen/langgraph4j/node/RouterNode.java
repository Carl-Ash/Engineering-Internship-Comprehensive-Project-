package com.carl.codegen.langgraph4j.node;

import com.carl.codegen.ai.AiCodeGenRouter;
import com.carl.codegen.langgraph4j.state.WorkflowContext;
import com.carl.codegen.utils.SpringContextUtil;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 智能路由节点 — 根据用户提示词选择代码生成类型。
 */
@Slf4j
public class RouterNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.fromState(state);
            log.info("执行节点: 智能路由");

            CodeGenTypeEnum codeGenType;
            try {
                // 调用 AI 路由选择生成类型
                AiCodeGenRouter router = SpringContextUtil.getBean(AiCodeGenRouter.class);
                codeGenType = router.routeCodeGenType(context.getOriginalPrompt());
                log.info("智能路由完成，类型: {} ({})", codeGenType.getValue(), codeGenType.getText());
            } catch (Exception e) {
                log.error("智能路由失败，降级为 HTML: {}", e.getMessage());
                codeGenType = CodeGenTypeEnum.HTML;
            }

            // 更新状态
            context.setCurrentStep("智能路由");
            context.setCodeGenType(codeGenType);
            return WorkflowContext.toStateMap(context);
        });
    }
}

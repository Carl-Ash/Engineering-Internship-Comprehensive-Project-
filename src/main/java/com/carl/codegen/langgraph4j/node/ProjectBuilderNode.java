package com.carl.codegen.langgraph4j.node;

import com.carl.codegen.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 项目构建节点 — 将生成的代码打包为可运行的项目。
 */
@Slf4j
public class ProjectBuilderNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.fromState(state);
            log.info("执行节点: 项目构建");

            // TODO: 实际执行项目构建逻辑
            String buildOutputDir = "/tmp/build/fake-build";

            context.setCurrentStep("项目构建");
            context.setBuildOutputDir(buildOutputDir);
            log.info("项目构建完成，结果目录: {}", buildOutputDir);
            return WorkflowContext.toStateMap(context);
        });
    }
}

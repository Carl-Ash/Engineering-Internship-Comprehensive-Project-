package com.carl.codegen.langgraph4j;

import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.langgraph4j.node.CodeGeneratorNode;
import com.carl.codegen.langgraph4j.node.ImageCollectorNode;
import com.carl.codegen.langgraph4j.node.ProjectBuilderNode;
import com.carl.codegen.langgraph4j.node.PromptEnhancerNode;
import com.carl.codegen.langgraph4j.node.RouterNode;
import com.carl.codegen.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 代码生成工作流 — 串联图片收集、提示词增强、智能路由、代码生成、项目构建。
 */
@Slf4j
public class CodeGenWorkflow {

    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            // 构建工作流图：图片收集 → 提示词增强 → 智能路由 → 代码生成 → 项目构建
            return new MessagesStateGraph<String>()
                    .addNode("image_collector", ImageCollectorNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create())
                    .addNode("code_generator", CodeGeneratorNode.create())
                    .addNode("project_builder", ProjectBuilderNode.create())

                    .addEdge(START, "image_collector")
                    .addEdge("image_collector", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    .addEdge("router", "code_generator")
                    .addEdge("code_generator", "project_builder")
                    .addEdge("project_builder", END)

                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "工作流创建失败");
        }
    }

    /**
     * 执行工作流，返回最终上下文
     */
    public WorkflowContext executeWorkflow(String originalPrompt) {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();

        // 初始化上下文
        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt(originalPrompt)
                .currentStep("初始化")
                .build();

        // 输出工作流拓扑
        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("工作流图:\n{}", graph.content());
        log.info("开始执行代码生成工作流");

        // 流式执行每个节点
        WorkflowContext finalContext = null;
        int stepIndex = 1;
        for (NodeOutput<MessagesState<String>> nodeResult : workflow.stream(
                Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
            log.info("--- 第 {} 步完成 ---", stepIndex);
            WorkflowContext currentContext = WorkflowContext.fromState(nodeResult.state());
            if (currentContext != null) {
                finalContext = currentContext;
                log.info("当前步骤上下文: {}", currentContext);
            }
            stepIndex++;
        }
        log.info("代码生成工作流执行完成");
        return finalContext;
    }
}

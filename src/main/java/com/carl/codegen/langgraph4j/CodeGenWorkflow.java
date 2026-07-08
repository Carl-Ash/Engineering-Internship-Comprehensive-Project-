package com.carl.codegen.langgraph4j;

import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.langgraph4j.node.CodeGeneratorNode;
import com.carl.codegen.langgraph4j.node.CodeQualityCheckNode;
import com.carl.codegen.langgraph4j.node.ImageCollectorNode;
import com.carl.codegen.langgraph4j.node.ProjectBuilderNode;
import com.carl.codegen.langgraph4j.node.PromptEnhancerNode;
import com.carl.codegen.langgraph4j.node.RouterNode;
import com.carl.codegen.langgraph4j.state.QualityResult;
import com.carl.codegen.langgraph4j.state.WorkflowContext;
import cn.hutool.json.JSONUtil;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import reactor.core.publisher.Flux;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 代码生成工作流 — 串联图片收集、提示词增强、智能路由、代码生成、项目构建。
 */
@Slf4j
public class CodeGenWorkflow {

    /**
     * 质检重试最大次数，超过后跳过修复直接继续
     */
    private static final int MAX_QUALITY_RETRY = 3;

    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            // 构建工作流图：图片收集 → 提示词增强 → 智能路由 → 代码生成 → 质检 → 构建/重生成
            return new MessagesStateGraph<String>()
                    .addNode("image_collector", ImageCollectorNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create())
                    .addNode("code_generator", CodeGeneratorNode.create())
                    .addNode("code_quality_check", CodeQualityCheckNode.create())
                    .addNode("project_builder", ProjectBuilderNode.create())

                    .addEdge(START, "image_collector")
                    .addEdge("image_collector", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    .addEdge("router", "code_generator")
                    .addEdge("code_generator", "code_quality_check")
                    // 质检条件边：失败重生成，通过则按类型决定是否构建
                    .addConditionalEdges("code_quality_check",
                            edge_async(this::routeAfterQualityCheck),
                            Map.of(
                                    "build", "project_builder",
                                    "skip_build", END,
                                    "fail", "code_generator"
                            ))
                    .addEdge("project_builder", END)

                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "工作流创建失败");
        }
    }

    /**
     * 质检后路由：失败重生成，通过则按类型决定是否构建
     */
    private String routeAfterQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.fromState(state);
        QualityResult qualityResult = context.getQualityResult();
        // 质检失败 → 重新生成代码（超过重试上限则跳过）
        if (qualityResult == null || !qualityResult.getIsValid()) {
            int retryCount = context.getQualityCheckRetryCount() != null
                    ? context.getQualityCheckRetryCount() : 0;
            if (retryCount >= MAX_QUALITY_RETRY) {
                log.warn("已达最大重试次数({})，跳过质检修复继续后续流程", MAX_QUALITY_RETRY);
                return routeBuildOrSkip(state);
            }
            log.error("代码质检失败，重新生成代码（第 {} 次重试）", retryCount);
            return "fail";
        }
        // 质检通过 → 按类型决定是否构建
        log.info("代码质检通过，继续后续流程");
        return routeBuildOrSkip(state);
    }

    /**
     * 条件路由：HTML/MULTI_FILE 跳过构建，VUE3 进入项目构建
     */
    private String routeBuildOrSkip(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.fromState(state);
        CodeGenTypeEnum codeGenType = context.getCodeGenType();
        // HTML/MULTI_FILE 文件已由 AI 直接保存，无需构建
        if (codeGenType == CodeGenTypeEnum.HTML || codeGenType == CodeGenTypeEnum.MULTI_FILE) {
            return "skip_build";
        }
        return "build";
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
            // 提取每次迭代的上下文
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

    /**
     * 执行工作流，通过 Flux 流式输出 SSE 事件
     */
    public Flux<String> executeWorkflowWithFlux(String originalPrompt) {
        return Flux.create(sink -> {
            Thread.startVirtualThread(() -> {
                try {
                    CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                    WorkflowContext initialContext = WorkflowContext.builder()
                            .originalPrompt(originalPrompt)
                            .currentStep("初始化")
                            .build();

                    sink.next(formatSseEvent("workflow_start", Map.of(
                            "message", "开始执行代码生成工作流",
                            "originalPrompt", originalPrompt
                    )));

                    GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                    log.info("工作流图:\n{}", graph.content());

                    int stepIndex = 1;
                    for (NodeOutput<MessagesState<String>> nodeResult : workflow.stream(
                            Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
                        log.info("--- 第 {} 步完成 ---", stepIndex);
                        WorkflowContext currentContext = WorkflowContext.fromState(nodeResult.state());
                        if (currentContext != null) {
                            sink.next(formatSseEvent("step_completed", Map.of(
                                    "stepNumber", stepIndex,
                                    "currentStep", currentContext.getCurrentStep()
                            )));
                        }
                        stepIndex++;
                    }

                    sink.next(formatSseEvent("workflow_completed", Map.of(
                            "message", "代码生成工作流执行完成"
                    )));
                    log.info("代码生成工作流执行完成");
                    sink.complete();
                } catch (Exception e) {
                    log.error("工作流执行失败: {}", e.getMessage(), e);
                    sink.next(formatSseEvent("workflow_error", Map.of(
                            "error", e.getMessage()
                    )));
                    sink.error(e);
                }
            });
        });
    }

    /**
     * 格式化 SSE 事件
     */
    private String formatSseEvent(String eventType, Object data) {
        try {
            String jsonData = JSONUtil.toJsonStr(data);
            return "event: " + eventType + "\ndata: " + jsonData + "\n\n";
        } catch (Exception e) {
            log.error("SSE 格式化失败: {}", e.getMessage(), e);
            return "event: error\ndata: {\"error\":\"格式化失败\"}\n\n";
        }
    }
}

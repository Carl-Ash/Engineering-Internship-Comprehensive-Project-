package com.carl.codegen.langgraph4j;

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
 * 代码生成流水线 — 串联图片收集、提示词增强、智能路由、代码生成、项目构建五个阶段。
 */
@Slf4j
public class WorkflowApp {

    public static void main(String[] args) throws GraphStateException {
        CompiledGraph<MessagesState<String>> pipeline = new MessagesStateGraph<String>()
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

        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt("创建一个个人博客网站")
                .currentStep("初始化")
                .build();

        log.info("初始输入: {}", initialContext.getOriginalPrompt());
        log.info("流水线开始执行");

        GraphRepresentation graph = pipeline.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("流水线拓扑:\n{}", graph.content());

        int stepIndex = 1;
        for (NodeOutput<MessagesState<String>> nodeResult : pipeline.stream(Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
            log.info("--- 第 {} 阶段完成 ---", stepIndex);
            WorkflowContext currentContext = WorkflowContext.fromState(nodeResult.state());
            if (currentContext != null) {
                log.info("阶段上下文: {}", currentContext);
            }
            stepIndex++;
        }

        log.info("流水线执行完成");
    }
}

package com.carl.codegen.langgraph4j;

import com.carl.codegen.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 带上下文的工作流 — 节点间通过 {@link WorkflowContext} 共享状态。
 */
@Slf4j
public class SimpleStatefulWorkflowApp {

    /**
     * 创建可读写上下文的节点
     */
    static AsyncNodeAction<MessagesState<String>> createContextAwareNode(String nodeKey, String displayName) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.fromState(state);
            log.info("执行节点: {} - {}", nodeKey, displayName);
            if (context != null) {
                context.setCurrentStep(nodeKey);
            }
            return WorkflowContext.toStateMap(context);
        });
    }

    public static void main(String[] args) throws GraphStateException {
        CompiledGraph<MessagesState<String>> pipeline = new MessagesStateGraph<String>()
                .addNode("image_collector", createContextAwareNode("image_collector", "获取图片素材"))
                .addNode("prompt_enhancer", createContextAwareNode("prompt_enhancer", "增强提示词"))
                .addNode("router", createContextAwareNode("router", "智能路由选择"))
                .addNode("code_generator", createContextAwareNode("code_generator", "网站代码生成"))
                .addNode("project_builder", createContextAwareNode("project_builder", "项目构建"))

                .addEdge(START, "image_collector")
                .addEdge("image_collector", "prompt_enhancer")
                .addEdge("prompt_enhancer", "router")
                .addEdge("router", "code_generator")
                .addEdge("code_generator", "project_builder")
                .addEdge("project_builder", END)

                .compile();

        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt("创建一个简单的个人博客网站")
                .currentStep("初始化")
                .build();

        log.info("初始输入: {}", initialContext.getOriginalPrompt());
        log.info("工作流开始执行");

        GraphRepresentation graph = pipeline.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("工作流拓扑:\n{}", graph.content());

        int stepIndex = 1;
        for (NodeOutput<MessagesState<String>> nodeResult : pipeline.stream(Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
            log.info("--- 第 {} 阶段完成 ---", stepIndex);
            WorkflowContext currentContext = WorkflowContext.fromState(nodeResult.state());
            if (currentContext != null) {
                log.info("阶段上下文: {}", currentContext);
            }
            stepIndex++;
        }

        log.info("工作流执行完成");
    }
}

package com.carl.codegen.langgraph4j;

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
 * 代码生成工作流工作流
 * 将网站生成过程拆分为 5 个有序阶段，通过 LangGraph 串联为单向工作流：
 *   图片素材收集 → 提示词增强 → 智能路由 → 代码生成 → 项目构建
 */
@Slf4j
public class SimpleWorkflowApp {

    /**
     * 创建一个透传节点：仅记录日志，并将节点名称作为消息原样输出。
     *
     * @param nodeName 节点名称，同时作为该节点的输出消息
     * @return 异步节点动作
     */
    static AsyncNodeAction<MessagesState<String>> createPassthroughNode(String nodeName) {
        return node_async(state -> {
            log.info("执行节点: {}", nodeName);
            return Map.of("messages", nodeName);
        });
    }

    public static void main(String[] args) throws GraphStateException {
        // 构建工作流：声明阶段节点 → 串联依赖边 → 编译
        CompiledGraph<MessagesState<String>> pipeline = new MessagesStateGraph<String>()
                // 声明 5 个阶段节点
                .addNode("image_collector", createPassthroughNode("获取图片素材"))
                .addNode("prompt_enhancer", createPassthroughNode("增强提示词"))
                .addNode("router", createPassthroughNode("智能路由选择"))
                .addNode("code_generator", createPassthroughNode("网站代码生成"))
                .addNode("project_builder", createPassthroughNode("项目构建"))

                // 串联为单向工作流
                .addEdge(START, "image_collector")
                .addEdge("image_collector", "prompt_enhancer")
                .addEdge("prompt_enhancer", "router")
                .addEdge("router", "code_generator")
                .addEdge("code_generator", "project_builder")
                .addEdge("project_builder", END)

                .compile();

        log.info("工作流开始执行");

        // 输出 Mermaid 流程图，便于可视化工作流拓扑
        GraphRepresentation graph = pipeline.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("工作流拓扑:\n{}", graph.content());

        // 逐步执行，记录每个阶段的输出
        int stepIndex = 1;
        for (NodeOutput<MessagesState<String>> nodeResult : pipeline.stream(Map.of())) {
            log.info("--- 第 {} 阶段完成 ---", stepIndex);
            log.info("阶段输出: {}", nodeResult);
            stepIndex++;
        }

        log.info("工作流执行完成");
    }
}

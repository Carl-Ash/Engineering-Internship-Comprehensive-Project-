package com.carl.codegen.langgraph4j.node;

import com.carl.codegen.langgraph4j.ai.ImageCollectPlanService;
import com.carl.codegen.langgraph4j.state.ImageCollectPlan;
import com.carl.codegen.langgraph4j.state.ImageResource;
import com.carl.codegen.langgraph4j.state.WorkflowContext;
import com.carl.codegen.langgraph4j.tools.ImageSearchTool;
import com.carl.codegen.langgraph4j.tools.LogoGeneratorTool;
import com.carl.codegen.langgraph4j.tools.MermaidDiagramTool;
import com.carl.codegen.langgraph4j.tools.UndrawIllustrationTool;
import com.carl.codegen.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点 — AI 规划后并发执行图片收集。
 */
@Slf4j
public class ImageCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.fromState(state);
            String originalPrompt = context.getOriginalPrompt();
            List<ImageResource> images = new ArrayList<>();

            try {
                // 1. AI 规划：分析需求，生成收集计划
                ImageCollectPlanService planService = SpringContextUtil.getBean(ImageCollectPlanService.class);
                ImageCollectPlan plan = planService.planImageCollection(originalPrompt);
                log.info("图片收集计划生成完成，开始并发执行");

                // 2. 并发执行各类图片收集任务
                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();

                if (plan.getContentImageTasks() != null) {
                    ImageSearchTool searchTool = SpringContextUtil.getBean(ImageSearchTool.class);
                    for (ImageCollectPlan.ImageSearchTask task : plan.getContentImageTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                searchTool.searchContentImages(task.query())));
                    }
                }
                if (plan.getIllustrationTasks() != null) {
                    UndrawIllustrationTool illTool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    for (ImageCollectPlan.IllustrationTask task : plan.getIllustrationTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                illTool.searchIllustrations(task.query())));
                    }
                }
                if (plan.getDiagramTasks() != null) {
                    MermaidDiagramTool diagramTool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                    for (ImageCollectPlan.DiagramTask task : plan.getDiagramTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                diagramTool.generateDiagram(task.mermaidCode(), task.description())));
                    }
                }
                if (plan.getLogoTasks() != null) {
                    LogoGeneratorTool logoTool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                    for (ImageCollectPlan.LogoTask task : plan.getLogoTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                logoTool.generateLogos(task.description())));
                    }
                }

                // 3. 等待全部完成，汇总结果
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                for (CompletableFuture<List<ImageResource>> future : futures) {
                    List<ImageResource> result = future.get();
                    if (result != null) {
                        images.addAll(result);
                    }
                }
                log.info("并发图片收集完成，共 {} 张", images.size());
            } catch (Exception e) {
                log.error("图片收集失败: {}", e.getMessage(), e);
            }

            context.setCurrentStep("图片收集");
            context.setImageResources(images);
            return WorkflowContext.toStateMap(context);
        });
    }
}

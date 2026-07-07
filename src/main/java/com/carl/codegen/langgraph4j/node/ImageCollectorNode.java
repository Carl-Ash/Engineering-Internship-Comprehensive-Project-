package com.carl.codegen.langgraph4j.node;

import com.carl.codegen.langgraph4j.state.ImageCategoryEnum;
import com.carl.codegen.langgraph4j.state.ImageResource;
import com.carl.codegen.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.Arrays;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点 — 从用户输入中提取图片资源。
 */
@Slf4j
public class ImageCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.fromState(state);
            log.info("执行节点: 图片收集");

            // TODO: 实际执行图片收集逻辑
            List<ImageResource> imageResources = Arrays.asList(
                    ImageResource.builder()
                            .category(ImageCategoryEnum.CONTENT)
                            .description("示例图片1")
                            .url("https://example.com/image1.png")
                            .build(),
                    ImageResource.builder()
                            .category(ImageCategoryEnum.LOGO)
                            .description("示例图片2")
                            .url("https://example.com/logo.png")
                            .build()
            );

            context.setCurrentStep("图片收集");
            context.setImageResources(imageResources);
            log.info("图片收集完成，共收集 {} 张图片", imageResources.size());
            return WorkflowContext.toStateMap(context);
        });
    }
}

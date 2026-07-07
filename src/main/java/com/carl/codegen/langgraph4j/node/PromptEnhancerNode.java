package com.carl.codegen.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.carl.codegen.langgraph4j.state.ImageResource;
import com.carl.codegen.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 提示词增强节点 — 将图片资源拼接到原始提示词中。
 */
@Slf4j
public class PromptEnhancerNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.fromState(state);
            log.info("执行节点: 提示词增强");

            String originalPrompt = context.getOriginalPrompt();
            String imageListRaw = context.getImageListRaw();
            List<ImageResource> imageResources = context.getImageResources();

            // 拼接图片资源到提示词
            StringBuilder builder = new StringBuilder();
            builder.append(originalPrompt);

            if (CollUtil.isNotEmpty(imageResources) || StrUtil.isNotBlank(imageListRaw)) {
                builder.append("\n\n## 可用素材资源\n");
                builder.append("请在生成网站使用以下图片资源，将这些图片合理地嵌入到网站的相应位置中。\n");
                if (CollUtil.isNotEmpty(imageResources)) {
                    for (ImageResource image : imageResources) {
                        builder.append("- ")
                                .append(image.getCategory().getText())
                                .append("：")
                                .append(image.getDescription())
                                .append("（")
                                .append(image.getUrl())
                                .append("）\n");
                    }
                } else {
                    builder.append(imageListRaw);
                }
            }

            String enhancedPrompt = builder.toString();
            // 更新状态
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancedPrompt);
            log.info("提示词增强完成，增强后长度: {} 字符", enhancedPrompt.length());
            return WorkflowContext.toStateMap(context);
        });
    }
}

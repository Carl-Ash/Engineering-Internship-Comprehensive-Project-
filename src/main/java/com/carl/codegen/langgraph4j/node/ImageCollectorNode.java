package com.carl.codegen.langgraph4j.node;

import com.carl.codegen.langgraph4j.ai.ImageCollectService;
import com.carl.codegen.langgraph4j.state.WorkflowContext;
import com.carl.codegen.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点 — 调用 AI 服务收集图片资源。
 */
@Slf4j
public class ImageCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.fromState(state);
            String originalPrompt = context.getOriginalPrompt();
            String imageListRaw = "";
            try {
                // 获取 AI 图片收集服务并调用
                ImageCollectService service = SpringContextUtil.getBean(ImageCollectService.class);
                imageListRaw = service.collectImages(originalPrompt);
            } catch (Exception e) {
                log.error("图片收集失败: {}", e.getMessage(), e);
            }
            // 更新状态
            context.setCurrentStep("图片收集");
            context.setImageListRaw(imageListRaw);
            return WorkflowContext.toStateMap(context);
        });
    }
}

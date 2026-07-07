package com.carl.codegen.langgraph4j.state;

import com.carl.codegen.model.enums.CodeGenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 工作流共享上下文，挂在 MessagesState 中随节点传递。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {

    // WorkflowContext 在 MessagesState 中的存储键
    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    // 当前执行阶段
    private String currentStep;

    // 用户原始输入的提示词
    private String originalPrompt;

    // 图片资源原始文本
    private String imageListRaw;

    // 图片资源列表
    private List<ImageResource> imageResources;

    // 增强后的提示词
    private String enhancedPrompt;

    // 代码生成类型
    private CodeGenTypeEnum codeGenType;

    // 代码生成产物目录
    private String codeOutputDir;

    // 项目构建产物目录
    private String buildOutputDir;

    // 质量检查结果
    private QualityResult qualityResult;

    // 错误信息
    private String errorMessage;

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 与 MessagesState 互转 ==========

    // 从 MessagesState 中取出 WorkflowContext
    public static WorkflowContext fromState(MessagesState<String> state) {
        return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
    }

    // 将 WorkflowContext 写入 MessagesState
    public static Map<String, Object> toStateMap(WorkflowContext context) {
        return Map.of(WORKFLOW_CONTEXT_KEY, context);
    }
}

package com.carl.codegen.langgraph4j.node;

import com.carl.codegen.core.builder.VueBuilder;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.langgraph4j.state.WorkflowContext;
import com.carl.codegen.utils.SpringContextUtil;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 项目构建节点 — 对 Vue 项目执行 npm install + npm run build。
 */
@Slf4j
public class ProjectBuilderNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.fromState(state);
            log.info("执行节点: 项目构建");

            String codeOutputDir = context.getCodeOutputDir();
            CodeGenTypeEnum codeGenType = context.getCodeGenType();
            String buildOutputDir;

            if (codeGenType == CodeGenTypeEnum.VUE3) {
                // Vue3 项目：npm install + npm run build
                try {
                    VueBuilder vueBuilder = SpringContextUtil.getBean(VueBuilder.class);
                    boolean buildSuccess = vueBuilder.buildProject(codeOutputDir);
                    if (buildSuccess) {
                        buildOutputDir = codeOutputDir + File.separator + "dist";
                        log.info("Vue 项目构建成功，dist 目录: {}", buildOutputDir);
                    } else {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败");
                    }
                } catch (Exception e) {
                    log.error("Vue 项目构建异常: {}", e.getMessage(), e);
                    buildOutputDir = codeOutputDir;
                }
            } else {
                // 其他类型：文件已由 AI 工具调用直接保存
                buildOutputDir = codeOutputDir;
            }

            // 更新状态
            context.setCurrentStep("项目构建");
            context.setBuildOutputDir(buildOutputDir);
            log.info("项目构建节点完成，最终目录: {}", buildOutputDir);
            return WorkflowContext.toStateMap(context);
        });
    }
}

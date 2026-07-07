package com.carl.codegen.ai.tools;

import cn.hutool.json.JSONObject;
import com.carl.codegen.constant.AppConstant;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 工具基类，定义所有工具的通用接口。
 */
public abstract class BaseTool {

    protected final String codeGenType;

    public BaseTool() {
        this.codeGenType = "vue3";
    }

    public BaseTool(String codeGenType) {
        this.codeGenType = codeGenType;
    }

    /**
     * 解析文件相对路径，返回项目目录下的绝对路径，并校验路径不越界
     */
    protected Path resolvePath(String relativePath, Long appId) {
        Path path = Paths.get(relativePath);
        if (!path.isAbsolute()) {
            String projectDirName = codeGenType + "_" + appId;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName).toAbsolutePath().normalize();
            path = projectRoot.resolve(relativePath).normalize();
            if (!path.startsWith(projectRoot)) {
                throw new SecurityException("路径越界: " + relativePath);
            }
        }
        return path;
    }

    /**
     * 生成 [工具调用] 前缀，子类在 generateToolExecutedResult 中可复用
     */
    protected String formatToolCall(String detail) {
        return String.format("[工具调用] %s %s", getDisplayName(), detail);
    }

    /**
     * 获取工具的英文名称（对应方法名）
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     */
    public abstract String getDisplayName();

    /**
     * 生成工具请求时的返回值（显示给用户）
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);
}

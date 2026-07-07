package com.carl.codegen.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具，AI 可通过工具调用的方式将生成的代码写入文件。
 */
@Slf4j
@Component
public class FileWriteTool extends BaseTool {

    public FileWriteTool() {
    }

    public FileWriteTool(String codeGenType) {
        super(codeGenType);
    }

    @Tool("写入文件到指定路径，每次调用只写入一个文件，多个文件请多次调用")
    public String writeFile(
            @P("文件的相对路径，例如 index.html、src/components/Header.vue")
            String relativeFilePath,
            @P("要写入文件的完整内容")
            String content,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = resolvePath(relativeFilePath, appId);
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        return String.format("""
                %s
                ```%s
                %s
                ```
                """, formatToolCall(relativeFilePath), suffix, content);
    }
}

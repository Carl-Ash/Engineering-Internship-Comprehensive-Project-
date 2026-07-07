package com.carl.codegen.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.langgraph4j.state.ImageCategoryEnum;
import com.carl.codegen.langgraph4j.state.ImageResource;
import com.carl.codegen.manager.CosManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Mermaid 架构图生成工具 — 通过 mermaid-cli 将文本转为图片并上传 COS。
 */
@Slf4j
@Component
public class MermaidDiagramTool {

    @Resource
    private CosManager cosManager;

    @Tool("将 Mermaid 代码转换为架构图图片，用于展示系统结构和技术关系")
    public List<ImageResource> generateDiagram(@P("Mermaid 图表代码") String mermaidCode,
                                               @P("架构图描述") String description) {
        if (StrUtil.isBlank(mermaidCode)) {
            return new ArrayList<>();
        }
        try {
            // 1. mermaid-cli 文本转 SVG
            File diagramFile = convertToSvg(mermaidCode);
            // 2. 上传 SVG 到 COS
            String cosKey = String.format("/mermaid/%s/%s",
                    RandomUtil.randomString(5), diagramFile.getName());
            String url = cosManager.uploadFile(cosKey, diagramFile);
            // 3. 清理本地临时文件
            FileUtil.del(diagramFile);

            if (StrUtil.isNotBlank(url)) {
                return List.of(ImageResource.builder()
                        .category(ImageCategoryEnum.ARCHITECTURE)
                        .description(description)
                        .url(url)
                        .build());
            }
        } catch (Exception e) {
            log.error("生成架构图失败: {}", e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    /**
     * 通过 mermaid-cli 将 Mermaid 代码转为 SVG 文件。
     */
    private File convertToSvg(String mermaidCode) {
        // 写入 Mermaid 代码到临时文件
        File inputFile = FileUtil.createTempFile("mermaid_input_", ".mmd", true);
        FileUtil.writeUtf8String(mermaidCode, inputFile);
        File outputFile = FileUtil.createTempFile("mermaid_output_", ".svg", true);

        // 执行 mmdc 命令转换
        String cliCommand = SystemUtil.getOsInfo().isWindows() ? "mmdc.cmd" : "mmdc";
        String commandLine = String.format("%s -i %s -o %s -b transparent",
                cliCommand,
                inputFile.getAbsolutePath(),
                outputFile.getAbsolutePath()
        );
        RuntimeUtil.execForStr(commandLine);

        // 清理输入文件，保留输出文件供上传
        FileUtil.del(inputFile);

        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Mermaid CLI 执行失败");
        }
        return outputFile;
    }
}

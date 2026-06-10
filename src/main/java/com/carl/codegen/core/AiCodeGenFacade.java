package com.carl.codegen.core;


import com.carl.codegen.ai.AiCodeGenService;
import com.carl.codegen.ai.model.HtmlResult;
import com.carl.codegen.ai.model.MultiFileResult;
import com.carl.codegen.core.parser.CodeParserExe;
import com.carl.codegen.core.saver.CodeSaverExe;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * 代码生成门面，编排 AI 生成 → 解析 → 保存 的完整流程。
 * <p>
 * 提供两种调用模式：
 * <ul>
 *   <li>同步（{@link #generateAndSave}）：阻塞等待 AI 返回完整结果后保存，返回保存目录</li>
 *   <li>流式（{@link #streamAndSave}）：实时透传 AI 流式输出，流结束后自动解析并保存</li>
 * </ul>
 */
@Service
@Slf4j
public class AiCodeGenFacade {

    @Resource
    private AiCodeGenService aiCodeGenService;

    /**
     * 同步生成代码并保存，阻塞直到 AI 返回完整结果。
     *
     * @param prompt 用户提示词
     * @param type   生成类型（HTML 单文件 / 多文件）
     * @return 保存的目录
     */
    public File generateAndSave(String prompt, CodeGenTypeEnum type) {
        if (type == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        return switch (type) {
            case HTML -> {
                HtmlResult parsed = aiCodeGenService.generateHtmlCode(prompt);
                yield CodeSaverExe.executeSaver(parsed, CodeGenTypeEnum.HTML);
            }
            case MULTI_FILE -> {
                MultiFileResult parsed = aiCodeGenService.generateMultiFileCode(prompt);
                yield CodeSaverExe.executeSaver(parsed, CodeGenTypeEnum.MULTI_FILE);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型" + type.getValue());
        };
    }

    /**
     * 流式生成代码并保存，实时透传 AI 输出，流结束后自动解析并写入文件。
     *
     * @param prompt 用户提示词
     * @param type   生成类型（HTML 单文件 / 多文件）
     * @return 实时透传的代码流
     */
    public Flux<String> streamAndSave(String prompt, CodeGenTypeEnum type) {
        if (type == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        return switch (type) {
            case HTML -> {
                Flux<String> codeFlux = aiCodeGenService.generateHtmlCodeStreaming(prompt);
                yield processCodeStream(codeFlux, CodeGenTypeEnum.HTML);
            }
            case MULTI_FILE -> {
                Flux<String> codeFlux = aiCodeGenService.generateMultiFileCodeStreaming(prompt);
                yield processCodeStream(codeFlux, CodeGenTypeEnum.MULTI_FILE);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型" + type.getValue());
        };
    }


    /**
     * 处理代码流，根据类型解析并保存。
     * @param codeStream 代码流
     * @param type       生成类型（HTML 单文件 / 多文件）
     * @return 保存后的目录流
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum type) {
        // 字符串拼接器，用于收集所有代码片段
        StringBuilder codeBuilder = new StringBuilder();
        // 累加代码片段
        return codeStream.doOnNext(codeBuilder::append).doOnComplete(() -> {
                try {
                    // 拼接所有代码片段
                    String fullCode = codeBuilder.toString();
                    // 解析代码
                    Object parsed = CodeParserExe.executeParser(fullCode, type);
                    // 保存代码
                    File savedDir = CodeSaverExe.executeSaver(parsed, type);
                    log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
                } catch (Exception e) {
                    log.error("保存失败: {}", e.getMessage());
                }
                });
    }
}

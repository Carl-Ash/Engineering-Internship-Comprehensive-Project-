package com.carl.codegen.core;

import cn.hutool.json.JSONUtil;
import com.carl.codegen.ai.AiCodeGenServiceFactory;
import com.carl.codegen.ai.model.HtmlResult;
import com.carl.codegen.ai.model.MultiFileResult;
import com.carl.codegen.ai.model.message.AiResponseMessage;
import com.carl.codegen.ai.model.message.ThinkingMessage;
import com.carl.codegen.ai.model.message.ToolRequestMessage;
import com.carl.codegen.ai.model.message.ToolResultMessage;
import com.carl.codegen.constant.AppConstant;
import com.carl.codegen.core.parser.CodeParserExe;
import com.carl.codegen.core.saver.CodeSaverExe;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.mapper.AppMapper;
import com.carl.codegen.model.entity.App;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 代码生成门面，编排 AI 生成 → 解析 → 保存 的完整流程。
 */
@Service
@Slf4j
public class AiCodeGenFacade {

    @Resource
    private AiCodeGenServiceFactory aiCodeGenServiceFactory;

    @Resource
    private AppMapper appMapper;

    /** 取消标记：key=appId，value=true表示已取消 */
    private final Map<Long, AtomicBoolean> cancelMap = new ConcurrentHashMap<>();

    /**
     * 发送取消信号
     */
    public void cancelGeneration(Long appId) {
        AtomicBoolean flag = cancelMap.computeIfAbsent(appId, k -> new AtomicBoolean(false));
        flag.set(true);
    }

    /**
     * 清理取消标记
     */
    private void clearCancel(Long appId) {
        cancelMap.remove(appId);
    }

    private boolean isCancelled(Long appId) {
        AtomicBoolean flag = cancelMap.get(appId);
        return flag != null && flag.get();
    }

    /**
     * 同步生成代码并保存。
     */
    public File generateAndSave(String prompt, CodeGenTypeEnum type, Long appId) {
        if (type == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        return switch (type) {
            case HTML -> {
                HtmlResult parsed = aiCodeGenServiceFactory.getAiCodeGenService(appId, type).generateHtmlCode(prompt);
                yield CodeSaverExe.executeSaver(parsed, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileResult parsed = aiCodeGenServiceFactory.getAiCodeGenService(appId, type).generateMultiFileCode(prompt);
                yield CodeSaverExe.executeSaver(parsed, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型" + type.getValue());
        };
    }

    /**
     * 流式生成代码并保存，实时透传 AI 输出，流结束后自动解析并写入文件。
     */
    public Flux<String> genAndSavestream(String prompt, CodeGenTypeEnum type, Long appId) {
        if (type == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        return switch (type) {
            case HTML -> {
                Flux<String> codeFlux = aiCodeGenServiceFactory.getAiCodeGenService(appId, type).generateHtmlCodeStreaming(prompt);
                yield processCodeStream(codeFlux, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeFlux = aiCodeGenServiceFactory.getAiCodeGenService(appId, type).generateMultiFileCodeStreaming(prompt);
                yield processCodeStream(codeFlux, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE3 -> {
                TokenStream tokenStream = aiCodeGenServiceFactory.getAiCodeGenService(appId, type).generateVue3CodeStreaming(appId, prompt);
                yield processTokenStream(tokenStream);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型" + type.getValue());
        };
    }

    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialThinking((PartialThinking partialThinking) -> {
                        ThinkingMessage thinkingMessage = new ThinkingMessage(partialThinking);
                        sink.next(JSONUtil.toJsonStr(thinkingMessage));
                    })
                    .beforeToolExecution((BeforeToolExecution before) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(before.request());
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolResultMessage toolResultMessage = new ToolResultMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolResultMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }

    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum type, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                .takeUntil(chunk -> isCancelled(appId))
                .doOnNext(codeBuilder::append)
                .doOnComplete(() -> {
                    clearCancel(appId);
                    try {
                        App app = appMapper.selectOneById(appId);
                        int newVersion = (app != null && app.getVersion() != null) ? app.getVersion() + 1 : 1;
                        if (type == CodeGenTypeEnum.VUE3) {
                            // VUE3 通过工具调用写入文件，无需额外解析保存
                            log.info("VUE3 生成完成，appId: {}，版本：{}", appId, newVersion);
                        } else {
                            String fullCode = codeBuilder.toString();
                            Object parsed = CodeParserExe.executeParser(fullCode, type);
                            File savedDir = CodeSaverExe.executeSaver(parsed, type, appId);
                            log.info("保存成功，路径：{}，版本：{}", savedDir.getAbsolutePath(), newVersion);
                        }
                        updateAppStatus(appId, AppConstant.GEN_STATUS_COMPLETED, newVersion);
                    } catch (Exception e) {
                        log.error("保存失败: {}", e.getMessage());
                        updateAppStatus(appId, AppConstant.GEN_STATUS_FAILED, null);
                    }
                })
                .doOnCancel(() -> {
                    clearCancel(appId);
                    updateAppStatus(appId, AppConstant.GEN_STATUS_FAILED, null);
                    log.info("生成已被取消，appId={}", appId);
                });
    }

    private void updateAppStatus(Long appId, String genStatus, Integer version) {
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setGenStatus(genStatus);
        if (version != null) {
            updateApp.setVersion(version);
        }
        appMapper.update(updateApp);
    }
}

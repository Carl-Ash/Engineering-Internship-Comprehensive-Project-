package com.carl.codegen.core;

import cn.hutool.json.JSONUtil;
import com.carl.codegen.ai.AiCodeGenService;
import com.carl.codegen.ai.AiCodeGenServiceFactory;
import com.carl.codegen.ai.model.HtmlResult;
import com.carl.codegen.ai.model.MultiFileResult;
import com.carl.codegen.constant.AppConstant;
import com.carl.codegen.core.builder.VueBuilder;
import com.carl.codegen.core.parser.CodeParserExe;
import com.carl.codegen.core.saver.CodeSaverExe;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.mapper.AppMapper;
import com.carl.codegen.model.entity.App;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
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

    @Resource
    private VueBuilder vueBuilder;

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
    public Flux<String> genAndSaveStream(String prompt, CodeGenTypeEnum type, Long appId) {
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
                boolean isModify = aiCodeGenServiceFactory.isModifyMode(appId);
                AiCodeGenService service = aiCodeGenServiceFactory.getAiCodeGenService(appId, type, isModify);
                yield processVue3Generation(service, prompt, appId);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型" + type.getValue());
        };
    }

    /**
     * VUE3 使用非流式 ChatModel 生成，避免 DeepSeek 流式 API 在工具调用时的
     * SSE 解析兼容性问题。工具调用由框架内部以请求/响应方式处理，不受影响。
     * <p>
     * 生成过程在后台线程执行，主线程每 10 秒发送心跳（"."）防止前端 60 秒超时。
     */
    private Flux<String> processVue3Generation(AiCodeGenService service, String prompt, Long appId) {
        return Flux.create(sink -> {
            try {
                sink.next("正在生成 Vue3 项目...\n");
                java.util.concurrent.atomic.AtomicReference<String> resultRef = new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.atomic.AtomicReference<Exception> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
                Thread genThread = Thread.ofVirtual()
                        .name("vue-gen-" + appId)
                        .start(() -> {
                            try {
                                resultRef.set(service.generateVue3Code(appId, prompt));
                            } catch (Exception e) {
                                errorRef.set(e);
                            }
                        });
                while (genThread.isAlive()) {
                    genThread.join(10000);
                    if (genThread.isAlive() && !sink.isCancelled()) {
                        sink.next("\n__hb__");
                    }
                }
                Exception genError = errorRef.get();
                if (genError != null) {
                    throw genError;
                }
                sink.next("\n" + resultRef.get());
                String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue3_" + appId;
                boolean buildSuccess = vueBuilder.buildProject(projectPath);
                if (!buildSuccess) {
                    sink.next("\n项目构建失败，请检查生成的文件或重试。");
                    updateAppStatus(appId, AppConstant.GEN_STATUS_FAILED, null);
                    sink.complete();
                    return;
                }
                App app = appMapper.selectOneById(appId);
                int newVersion = (app != null && app.getVersion() != null) ? app.getVersion() + 1 : 1;
                updateAppStatus(appId, AppConstant.GEN_STATUS_COMPLETED, newVersion);
                sink.complete();
            } catch (Exception e) {
                log.error("VUE3生成失败", e);
                updateAppStatus(appId, AppConstant.GEN_STATUS_FAILED, null);
                sink.error(e);
            }
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
                        String fullCode = codeBuilder.toString();
                        Object parsed = CodeParserExe.executeParser(fullCode, type);
                        File savedDir = CodeSaverExe.executeSaver(parsed, type, appId);
                        log.info("保存成功，路径：{}，版本：{}", savedDir.getAbsolutePath(), newVersion);
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

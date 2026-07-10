package com.carl.codegen.core;

import cn.hutool.core.io.FileUtil;
import com.carl.codegen.ai.AiCodeGenService;
import com.carl.codegen.ai.AiCodeGenServiceFactory;
import com.carl.codegen.constant.AppConstant;
import com.carl.codegen.core.builder.VueBuilder;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.mapper.AppMapper;
import com.carl.codegen.model.entity.App;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import com.carl.codegen.monitor.MonitorContext;
import com.carl.codegen.monitor.MonitorContextHolder;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
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

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

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
                aiCodeGenServiceFactory.getAiCodeGenService(appId, type).generateHtmlCode(appId, prompt);
                yield new File(AppConstant.CODE_OUTPUT_ROOT_DIR, "html_" + appId);
            }
            case MULTI_FILE -> {
                aiCodeGenServiceFactory.getAiCodeGenService(appId, type).generateMultiFileCode(appId, prompt);
                yield new File(AppConstant.CODE_OUTPUT_ROOT_DIR, "multi_file_" + appId);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型" + type.getValue());
        };
    }

    /**
     * 流式生成代码并保存。
     * <p>
     * HTML / MULTI_FILE 与 VUE3 均使用非流式 ChatModel，
     * 避免 DeepSeek 流式 API 在工具调用时的 SSE 解析兼容性问题。
     */
    public Flux<String> genAndSaveStream(String prompt, CodeGenTypeEnum type, Long appId) {
        if (type == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        return switch (type) {
            case HTML -> {
                backupCurrentVersion(appId, CodeGenTypeEnum.HTML);
                yield generateSimpleWithStatus(prompt, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                backupCurrentVersion(appId, CodeGenTypeEnum.MULTI_FILE);
                yield generateSimpleWithStatus(prompt, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE3 -> {
                backupCurrentVersion(appId, CodeGenTypeEnum.VUE3);
                boolean isModify = aiCodeGenServiceFactory.isModifyMode(appId);
                AiCodeGenService service = aiCodeGenServiceFactory.getAiCodeGenService(appId, type, isModify);
                yield processVue3Generation(service, prompt, appId);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型" + type.getValue());
        };
    }

    /**
     * HTML / MULTI_FILE 使用非流式 ChatModel 生成，避免 DeepSeek 流式 API 在
     * 工具调用时的 SSE 解析兼容性问题（与 VUE3 策略一致）。
     * <p>
     * 生成过程在后台虚拟线程执行，主线程每 10 秒发送心跳（"\n__hb__"）防止前端 120 秒超时。
     */
    private Flux<String> generateSimpleWithStatus(String prompt, CodeGenTypeEnum type, Long appId) {
        MonitorContext ctx = MonitorContextHolder.getContext();
        return Flux.create(sink -> {
            try {
                sink.next("正在生成代码...\n");
                java.util.concurrent.atomic.AtomicReference<String> resultRef = new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.atomic.AtomicReference<Exception> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
                AiCodeGenService service = aiCodeGenServiceFactory.getAiCodeGenService(appId, type);
                Thread genThread = Thread.ofVirtual()
                        .name("simple-gen-" + appId)
                        .start(() -> {
                            MonitorContextHolder.setContext(ctx);
                            try {
                                if (type == CodeGenTypeEnum.HTML) {
                                    resultRef.set(service.generateHtmlCode(appId, prompt));
                                } else {
                                    resultRef.set(service.generateMultiFileCode(appId, prompt));
                                }
                            } catch (Exception e) {
                                errorRef.set(e);
                            } finally {
                                MonitorContextHolder.clearContext();
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
                String projectDirName = type.getValue() + "_" + appId;
                File savedDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                if (!savedDir.exists() || !savedDir.isDirectory()) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                            "生成目录不存在: " + savedDir.getAbsolutePath());
                }
                App app = appMapper.selectOneById(appId);
                int newVersion = (app != null && app.getVersion() != null) ? app.getVersion() + 1 : 1;
                updateAppStatus(appId, AppConstant.GEN_STATUS_COMPLETED, newVersion);
                log.info("{} 生成完成，路径：{}，版本：{}", type.getValue(), savedDir.getAbsolutePath(), newVersion);
                sink.next(resultRef.get() != null ? resultRef.get() : "\n代码生成完成\n");
                sink.complete();
            } catch (Exception e) {
                log.error("{} 生成失败", type.getValue(), e);
                updateAppStatus(appId, AppConstant.GEN_STATUS_FAILED, null);
                clearChatMemory(appId);
                String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                sink.next("\n生成失败：" + cause);
                sink.complete();
            }
        });
    }

    /**
     * VUE3 使用非流式 ChatModel 生成，避免 DeepSeek 流式 API 在工具调用时的
     * SSE 解析兼容性问题。工具调用由框架内部以请求/响应方式处理，不受影响。
     * <p>
     * 生成过程在后台线程执行，主线程每 10 秒发送心跳（"."）防止前端 60 秒超时。
     */
    private Flux<String> processVue3Generation(AiCodeGenService service, String prompt, Long appId) {
        MonitorContext ctx = MonitorContextHolder.getContext();
        return Flux.create(sink -> {
            try {
                sink.next("正在生成 Vue3 项目...\n");
                java.util.concurrent.atomic.AtomicReference<String> resultRef = new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.atomic.AtomicReference<Exception> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
                Thread genThread = Thread.ofVirtual()
                        .name("vue-gen-" + appId)
                        .start(() -> {
                            MonitorContextHolder.setContext(ctx);
                            try {
                                resultRef.set(service.generateVue3Code(appId, prompt));
                            } catch (Exception e) {
                                errorRef.set(e);
                            } finally {
                                MonitorContextHolder.clearContext();
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
                clearChatMemory(appId);
                String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                sink.next("\n生成失败：" + cause);
                sink.complete();
            }
        });
    }

    /**
     * 生成新版本前备份当前代码到版本化目录。
     */
    private void backupCurrentVersion(Long appId, CodeGenTypeEnum type) {
        String sourceDirName = type.getValue() + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            return;
        }
        App app = appMapper.selectOneById(appId);
        int currentVersion = (app != null && app.getVersion() != null) ? app.getVersion() : 0;
        if (currentVersion <= 0) {
            return;
        }
        String backupDirName = sourceDirName + "_v" + currentVersion;
        String backupDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + backupDirName;
        File backupDir = new File(backupDirPath);
        if (backupDir.exists()) {
            FileUtil.del(backupDir);
        }
        FileUtil.copyContent(sourceDir, backupDir, true);
        log.info("版本 {} 已备份到: {}", currentVersion, backupDirPath);
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

    /**
     * 清理 Redis 中的聊天记忆，避免生成失败后重试时发送历史消息。
     */
    private void clearChatMemory(Long appId) {
        try {
            redisChatMemoryStore.deleteMessages(appId);
            log.info("已清理 appId={} 的 Redis 聊天记忆", appId);
        } catch (Exception e) {
            log.warn("清理 Redis 聊天记忆失败, appId={}, error={}", appId, e.getMessage());
        }
    }
}

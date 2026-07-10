package com.carl.codegen.ai;

import com.carl.codegen.ai.guardrail.PromptSafetyInputGuardrail;
import com.carl.codegen.ai.tools.BaseTool;
import com.carl.codegen.ai.tools.FileWriteTool;
import com.carl.codegen.ai.tools.ToolManager;
import com.carl.codegen.constant.AppConstant;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import com.carl.codegen.service.ChatHistoryService;
import com.carl.codegen.utils.SpringContextUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;

@Slf4j
@Configuration
public class AiCodeGenServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGenService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) ->
                    log.debug("AI 服务实例被移除，cacheKey: {}, 原因: {}", key, cause))
            .build();

    /**
     * 根据 appId 获取服务（带缓存）
     */
    public AiCodeGenService getAiCodeGenService(long appId) {
        return getAiCodeGenService(appId, CodeGenTypeEnum.HTML, false);
    }

    /**
     * 根据 appId 和 codeGenTypeEnum 获取服务（带缓存）
     */
    public AiCodeGenService getAiCodeGenService(long appId, CodeGenTypeEnum codeGenTypeEnum) {
        return getAiCodeGenService(appId, codeGenTypeEnum, false);
    }

    /**
     * 根据 appId、codeGenTypeEnum 和修改模式获取服务（带缓存）
     */
    public AiCodeGenService getAiCodeGenService(long appId, CodeGenTypeEnum codeGenTypeEnum, boolean isModify) {
        // 每次获取服务前先清理 Redis 中可能残留的不完整工具调用序列
        cleanupStaleToolCalls(appId);
        String cacheKey = createCacheKey(appId, codeGenTypeEnum, isModify);
        return serviceCache.get(cacheKey, key -> createAiCodeGenService(appId, codeGenTypeEnum, isModify));
    }

    /**
     * 清理 Redis 中可能残留的不完整工具调用序列和状态消息（如用户取消生成导致）。
     * 清理后驱逐缓存，确保下次请求使用干净的 ChatMemory 实例。
     */
    private void cleanupStaleToolCalls(long appId) {
        try {
            MessageWindowChatMemory tempMemory = MessageWindowChatMemory
                    .builder()
                    .id(appId)
                    .chatMemoryStore(redisChatMemoryStore)
                    .maxMessages(200)
                    .build();
            int before = tempMemory.messages().size();
            chatHistoryService.loadChatHistoryToMemory(appId, tempMemory, 200);
            int after = tempMemory.messages().size();
            if (after < before) {
                log.info("清理了 {} 条异常消息，驱逐 appId: {} 的缓存", before - after, appId);
                evictServiceCache(appId);
            }
        } catch (Exception e) {
            log.debug("清理过期工具调用失败，appId: {}, error: {}", appId, e.getMessage());
        }
    }

    /**
     * 驱逐指定 appId 的所有缓存条目（create 和 modify 模式）。
     */
    private void evictServiceCache(long appId) {
        for (CodeGenTypeEnum type : CodeGenTypeEnum.values()) {
            serviceCache.invalidate(createCacheKey(appId, type, false));
            serviceCache.invalidate(createCacheKey(appId, type, true));
        }
    }

    /**
     * 检测是否为修改模式（项目目录已有文件）
     */
    public boolean isModifyMode(Long appId) {
        java.io.File projectDir = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue3_" + appId).toFile();
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return false;
        }
        String[] files = projectDir.list();
        return files != null && files.length > 0;
    }

    /**
     * 创建新的 AI 服务实例 — 每次调用获取独立的原型 ChatModel/StreamingChatModel，解决并发阻塞问题
     */
    /** VUE3 生成涉及多轮工具调用，消息窗口过大时 prompt tokens 会超限 */
    private static final int VUE_CHAT_MEMORY_MAX_MESSAGES = 40;

    private AiCodeGenService createAiCodeGenService(long appId, CodeGenTypeEnum codeGenTypeEnum, boolean isModify) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        return switch (codeGenTypeEnum) {
            case HTML, MULTI_FILE -> {
                MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                        .builder()
                        .id(appId)
                        .chatMemoryStore(redisChatMemoryStore)
                        .maxMessages(200)
                        .build();
                chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 200);
                StreamingChatModel generalStreamingChatModel = SpringContextUtil.getBean(
                        "generalStreamingChatModel", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGenService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(generalStreamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools(new FileWriteTool(codeGenTypeEnum.getValue()))
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        .maxSequentialToolsInvocations(100)
                        .build();
            }
            case VUE3 -> {
                MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                        .builder()
                        .id(appId)
                        .chatMemoryStore(redisChatMemoryStore)
                        .maxMessages(VUE_CHAT_MEMORY_MAX_MESSAGES)
                        .build();
                chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, VUE_CHAT_MEMORY_MAX_MESSAGES);
                BaseTool[] tools = toolManager.getAllTools();
                if (isModify) {
                    tools = Arrays.stream(tools)
                            .filter(t -> !(t instanceof FileWriteTool))
                            .toArray(BaseTool[]::new);
                }
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean(
                        "reasoningStreamingChatModel", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGenService.class)
                        .streamingChatModel(reasoningStreamingChatModel)
                        .chatModel(chatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools((Object[]) tools)
                        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                                toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                        ))
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        .maxSequentialToolsInvocations(100)
                        .build();
            }

            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenTypeEnum.getValue());
        };
    }

    /**
     * 默认 Bean，保证兼容
     */
    @Bean
    public AiCodeGenService aiCodeGenService() {
        return getAiCodeGenService(0L);
    }

    private String createCacheKey(long appId, CodeGenTypeEnum codeGenTypeEnum, boolean isModify) {
        return appId + "_" + codeGenTypeEnum.getValue() + (isModify ? "_modify" : "_create");
    }
}

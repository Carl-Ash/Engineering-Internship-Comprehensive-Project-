package com.carl.codegen.ai;

import com.carl.codegen.ai.tools.BaseTool;
import com.carl.codegen.ai.tools.FileWriteTool;
import com.carl.codegen.ai.tools.ToolManager;
import com.carl.codegen.constant.AppConstant;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import com.carl.codegen.service.ChatHistoryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
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

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel openAiStreamingChatModel;

    @Resource
    private StreamingChatModel reasoningStreamingChatModel;

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
        String cacheKey = createCacheKey(appId, codeGenTypeEnum, isModify);
        return serviceCache.get(cacheKey, key -> createAiCodeGenService(appId, codeGenTypeEnum, isModify));
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
     * 创建新的 AI 服务实例
     */
    private AiCodeGenService createAiCodeGenService(long appId, CodeGenTypeEnum codeGenTypeEnum, boolean isModify) {
        // 创建新的 AI 服务实例
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return switch (codeGenTypeEnum) {
            case HTML, MULTI_FILE -> AiServices.builder(AiCodeGenService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModel)
                    .chatMemory(chatMemory)
                    .tools(new FileWriteTool(codeGenTypeEnum.getValue()))
                    .build();
            case VUE3 -> {
                BaseTool[] tools = toolManager.getAllTools();
                if (isModify) {
                    tools = Arrays.stream(tools)
                            .filter(t -> !(t instanceof FileWriteTool))
                            .toArray(BaseTool[]::new);
                }
                yield AiServices.builder(AiCodeGenService.class)
                        .streamingChatModel(reasoningStreamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools(tools)
                        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                                toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                        ))
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

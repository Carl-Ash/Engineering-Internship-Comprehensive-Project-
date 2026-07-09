package com.carl.codegen.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.carl.codegen.constant.UserConstant;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.exception.ThrowUtils;
import com.carl.codegen.model.dto.chathistory.ChatHistoryQueryRequest;
import com.carl.codegen.model.entity.App;
import com.carl.codegen.model.entity.User;
import com.carl.codegen.model.enums.ChatHistoryMessageTypeEnum;
import com.carl.codegen.service.AppService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.carl.codegen.model.entity.ChatHistory;
import com.carl.codegen.mapper.ChatHistoryMapper;
import com.carl.codegen.service.ChatHistoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 对话历史 服务层实现。
 *
 */
@Slf4j
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    @Lazy
    private AppService appService;

    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        // Redis 中已有完整会话上下文（含工具调用），无需从 DB 加载
        if (!chatMemory.messages().isEmpty()) {
            int removed = cleanupIncompleteToolCalls(chatMemory);
            log.debug("Redis 中已存在 appId: {} 的会话上下文，共 {} 条消息，清理 {} 条不完整工具调用",
                    appId, chatMemory.messages().size(), removed);
            return chatMemory.messages().size();
        }
        // Redis 为空（首次使用或过期），从 DB 加载历史作为回退
        try {
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false)
                    .limit(1, maxCount);
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) return 0;
            Collections.reverse(historyList);
            int loadedCount = 0;
            for (ChatHistory history : historyList) {
                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    loadedCount++;
                }
            }
            log.info("从 DB 为 appId: {} 加载了 {} 条历史对话", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public String exportToMarkdown(Long appId, String appName, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权导出该应用的对话历史");
        // 查询所有对话历史，按时间正序
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(ChatHistory::getAppId, appId)
                .orderBy(ChatHistory::getCreateTime, true);
        List<ChatHistory> historyList = this.list(queryWrapper);
        if (CollUtil.isEmpty(historyList)) {
            return "";
        }
        StringBuilder md = new StringBuilder();
        md.append("# 对话历史 - ").append(appName != null ? appName : "应用").append("\n\n");
        md.append("> 导出时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        md.append("---\n\n");
        for (ChatHistory history : historyList) {
            if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                md.append("## 用户\n\n");
            } else {
                md.append("## AI\n\n");
            }
            md.append(history.getMessage()).append("\n\n");
            md.append("---\n\n");
        }
        return md.toString();
    }

    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId) {
        // 校验参数
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(ChatHistoryMessageTypeEnum.getEnumByValue(messageType) == null, ErrorCode.PARAMS_ERROR, "不支持的消息类型: " + messageType);

        // 保存到数据库
        ChatHistory chatHistory = ChatHistory.builder().appId(appId).message(message).messageType(messageType).userId(userId).isDelete(0).build();
        return this.save(chatHistory);
    }

    @Override
    public boolean deleteChatHistoryById(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "对话历史ID不能为空");
        return this.removeById(id);
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        return this.remove(queryWrapper);
    }

    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq("id", id)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("appId", appId)
                .eq("userId", userId);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }

    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                                      LocalDateTime lastCreateTime,
                                                      User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(queryRequest);
        // 查询数据
        return this.page(Page.of(1, pageSize), queryWrapper);
    }

    /**
     * 清理 Redis 聊天记忆中不完整的工具调用序列。
     * 当 AI 消息带有 tool_calls 但缺少对应的 ToolExecutionResultMessage 时，
     * 移除这些不完整的消息，避免 OpenAI API 报错。
     *
     * @return 被移除的消息数量
     */
    private int cleanupIncompleteToolCalls(MessageWindowChatMemory chatMemory) {
        List<ChatMessage> messages = chatMemory.messages();
        int initialSize = messages.size();

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                var toolCallIds = aiMsg.toolExecutionRequests().stream()
                        .map(req -> req.id())
                        .collect(java.util.stream.Collectors.toSet());

                for (int j = i + 1; j < messages.size(); j++) {
                    ChatMessage nextMsg = messages.get(j);
                    if (nextMsg instanceof ToolExecutionResultMessage toolResult) {
                        toolCallIds.remove(toolResult.id());
                    }
                    if (!(nextMsg instanceof ToolExecutionResultMessage)) {
                        break;
                    }
                }

                if (!toolCallIds.isEmpty()) {
                    log.warn("检测到不完整的工具调用序列，从索引 {} 截断，缺少 tool_call_id: {}",
                            i, toolCallIds);
                    messages.subList(i, messages.size()).clear();
                }
                break;
            }
        }

        return initialSize - messages.size();
    }

}

package com.carl.codegen.service;

import com.carl.codegen.model.dto.chathistory.ChatHistoryQueryRequest;
import com.carl.codegen.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.carl.codegen.model.entity.ChatHistory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author Carl
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话历史消息
     * @param appId 应用id
     * @param message 消息内容
     * @param messageType 消息类型 user/ai
     * @param userId 创建用户id
     * @return 是否添加成功
     */
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 根据id删除单条对话历史
     * @param id 对话历史id
     * @return 是否删除成功
     */
    boolean deleteChatHistoryById(Long id);

    /**
     * 根据应用id删除所有对话历史
     * @param appId 应用id
     * @return 是否删除成功
     */
    boolean deleteByAppId(Long appId);

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 导出对话历史为 Markdown 字符串
     * @param appId 应用id
     * @param appName 应用名称
     * @param loginUser 登录用户
     * @return Markdown 内容
     */
    String exportToMarkdown(Long appId, String appName, User loginUser);

    /**
     * 从数据库加载历史对话到记忆中
     * @param appId 应用id
     * @param chatMemory 对话记忆
     * @param maxCount 最多加载条数
     * @return 实际加载条数
     */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 分页查询单个应用的对话历史
     * @param appId 应用id
     * @param pageSize 分页大小
     * @param lastCreateTime 游标查询 - 最后一条记录的创建时间
     * @param loginUser 登录用户
     * @return 分页结果
     */
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                                      LocalDateTime lastCreateTime,
                                                      User loginUser);
}

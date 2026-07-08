package com.carl.codegen.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.carl.codegen.ai.model.message.*;
import com.carl.codegen.ai.tools.ToolManager;
import com.carl.codegen.model.enums.ChatHistoryMessageTypeEnum;
import com.carl.codegen.model.entity.User;
import com.carl.codegen.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * 解析流式 JSON 消息，重组为前端可展示的文本格式（含工具调用过程）
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private ToolManager toolManager;

    public Flux<String> handle(Flux<String> flux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StringBuilder responseCollector = new StringBuilder();
        Set<String> seenToolIds = new HashSet<>();
        return flux
                .map(chunk -> parseChunk(chunk, responseCollector, seenToolIds))
                .filter(StrUtil::isNotEmpty)
                .doOnComplete(() -> {
                    String aiResponse = responseCollector.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    private String parseChunk(String chunk, StringBuilder responseCollector, Set<String> seenToolIds) {
        StreamMessage message = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageType messageType = StreamMessageType.fromValue(message.getType().getValue());
        switch (messageType) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String content = aiMessage.getContent();
                responseCollector.append(content);
                return content;
            }
            case THINKING -> {
                ThinkingMessage thinking = JSONUtil.toBean(chunk, ThinkingMessage.class);
                String content = thinking.getContent();
                responseCollector.append("[思考] ").append(content).append("\n");
                return "\n\n[深度思考]\n" + content + "\n\n";
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequest = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequest.getId();
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    seenToolIds.add(toolId);
                    var tool = toolManager.getTool(toolRequest.getName());
                    if (tool != null) {
                        return tool.generateToolRequestResponse();
                    }
                    return "\n\n[选择工具] " + toolRequest.getName() + "\n\n";
                }
                return "";
            }
            case TOOL_RESULT -> {
                ToolResultMessage toolResult = JSONUtil.toBean(chunk, ToolResultMessage.class);
                var tool = toolManager.getTool(toolResult.getName());
                if (tool != null) {
                    JSONObject arguments = JSONUtil.parseObj(toolResult.getArguments());
                    String result = tool.generateToolExecutedResult(arguments);
                    responseCollector.append("\n\n").append(result).append("\n\n");
                    return "\n\n" + result + "\n\n";
                }
                responseCollector.append("\n\n").append(toolResult.getResult()).append("\n\n");
                return "\n\n" + toolResult.getResult() + "\n\n";
            }
            default -> {
                log.error("不支持的消息类型: {}", messageType);
                return "";
            }
        }
    }
}

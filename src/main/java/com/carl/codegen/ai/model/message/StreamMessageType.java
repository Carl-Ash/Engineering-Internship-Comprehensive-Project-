package com.carl.codegen.ai.model.message;

import lombok.Getter;

/**
 * 流式消息类型
 */
@Getter
public enum StreamMessageType {

    AI_RESPONSE("ai_response", "AI响应"),
    TOOL_REQUEST("tool_request", "工具请求"),
    TOOL_RESULT("tool_result", "工具执行结果");

    private final String value;
    private final String text;

    StreamMessageType(String value, String text) {
        this.value = value;
        this.text = text;
    }

    public static StreamMessageType fromValue(String value) {
        for (StreamMessageType type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}

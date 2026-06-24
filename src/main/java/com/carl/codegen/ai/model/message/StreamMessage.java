package com.carl.codegen.ai.model.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式消息基类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamMessage {
    private StreamMessageType type;
}

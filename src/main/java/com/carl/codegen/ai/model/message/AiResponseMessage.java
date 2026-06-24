package com.carl.codegen.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 响应消息
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class AiResponseMessage extends StreamMessage {

    private String content;

    public AiResponseMessage(String content) {
        super(StreamMessageType.AI_RESPONSE);
        this.content = content;
    }
}

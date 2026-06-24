package com.carl.codegen.ai.model.message;

import dev.langchain4j.model.chat.response.PartialThinking;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class ThinkingMessage extends StreamMessage {

    private String content;

    public ThinkingMessage(PartialThinking partialThinking) {
        super(StreamMessageType.THINKING);
        this.content = partialThinking.text();
    }
}

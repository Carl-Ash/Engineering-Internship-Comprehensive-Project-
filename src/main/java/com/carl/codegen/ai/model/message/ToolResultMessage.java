package com.carl.codegen.ai.model.message;

import dev.langchain4j.service.tool.ToolExecution;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolResultMessage extends StreamMessage {

    private String id;

    private String name;

    private String arguments;

    private String result;

    public ToolResultMessage(ToolExecution toolExecution) {
        super(StreamMessageType.TOOL_RESULT);
        this.id = toolExecution.request().id();
        this.name = toolExecution.request().name();
        this.arguments = toolExecution.request().arguments();
        this.result = toolExecution.result();
    }
}

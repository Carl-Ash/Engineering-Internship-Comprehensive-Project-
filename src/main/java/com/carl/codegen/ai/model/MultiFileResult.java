package com.carl.codegen.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 多文件返回的代码结果
 */
@Description("生成多个代码文件的结果")
@Data
public class MultiFileResult {
    /**
     * HTML代码
     */
    @Description("HTML代码")
    private String htmlCode;

    /**
     * CSS代码
     */
    @Description("CSS代码")
    private String cssCode;

    /**
     * JS代码
     */
    @Description("JS代码")
    private String jsCode;

    /**
     * 描述
     */
    @Description("多文件代码的描述")
    private String description;
}

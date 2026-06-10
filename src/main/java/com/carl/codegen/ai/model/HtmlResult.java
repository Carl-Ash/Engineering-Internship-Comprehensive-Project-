package com.carl.codegen.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * HTML返回的代码结果
 */
@Description("生成 HTML 代码文件的结果")
@Data
public class HtmlResult {

    /**
     * HTML代码
     */
    @Description("HTML代码")
    private String htmlCode;

    /**
     * 描述
     */
    @Description("HTML代码的描述")
    private String description;
}

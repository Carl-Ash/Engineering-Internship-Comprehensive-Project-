package com.carl.codegen.ai;

import com.carl.codegen.ai.model.HtmlResult;
import com.carl.codegen.ai.model.MultiFileResult;
import dev.langchain4j.service.SystemMessage;
import reactor.core.publisher.Flux;

public interface AiCodeGenService {

    /**
     * 生成HTML代码
     * @param prompt 代码提示
     * @return 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlResult generateHtmlCode(String prompt);

    /**
     * 生成多文件代码
     * @param prompt 代码提示
     * @return 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileResult generateMultiFileCode(String prompt);

    /**
     * 生成HTML代码
     * @param prompt 代码提示
     * @return 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStreaming(String prompt);

    /**
     * 生成多文件代码
     * @param prompt 代码提示
     * @return 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStreaming(String prompt);
}

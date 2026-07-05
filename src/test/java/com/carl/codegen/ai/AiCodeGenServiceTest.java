package com.carl.codegen.ai;

import com.carl.codegen.ai.model.HtmlResult;
import com.carl.codegen.ai.model.MultiFileResult;
import com.carl.codegen.core.AiCodeGenFacade;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;

@SpringBootTest
class AiCodeGenServiceTest {

    @Resource
    private AiCodeGenService aiCodeGenService;

    @Resource
    private AiCodeGenFacade aiCodeGenFacade;

    @Test
    void generateHtmlCode() {
        HtmlResult htmlResult = aiCodeGenService.generateHtmlCode("做个新手程序员的博客，不超过20行");
        Assertions.assertNotNull(htmlResult);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileResult multiFileResult = aiCodeGenService.generateMultiFileCode("做个新手程序员的博客，不超过50行");
        Assertions.assertNotNull(multiFileResult);
    }

    @Test
    void generateVue3CodeStreaming() {
        Flux<String> codeStream = aiCodeGenFacade.genAndSaveStream(
                "简单的任务记录网站，总代码量不超过200行",
                CodeGenTypeEnum.VUE3, 1L);
        List<String> result = codeStream.collectList().block();
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }
}
package com.carl.codegen.ai;

import com.carl.codegen.ai.model.HtmlResult;
import com.carl.codegen.ai.model.MultiFileResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiCodeGenServiceTest {

    @Resource
    private AiCodeGenService aiCodeGenService;

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
}
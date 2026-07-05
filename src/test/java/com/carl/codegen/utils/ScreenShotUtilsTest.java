package com.carl.codegen.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@Slf4j
@SpringBootTest
class ScreenShotUtilsTest {

    @Test
    void saveWebPageScreenshot() {
        String url = "https://www.bilibili.com/?spm_id_from=333.788.0.0";
        String screenshotPath = ScreenShotUtils.saveWebPageScreenshot(url);
        Assertions.assertNotNull(screenshotPath);

    }
}
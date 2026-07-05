package com.carl.codegen.service;

/**
 * 网页截图服务
 *
 * @author Carl
 */
public interface ScreenshotService {

    /**
     * 根据网页URL生成截图并上传到对象存储
     *
     * @param webUrl 目标网页URL
     * @return 截图访问URL
     */
    String generateAndUploadScreenshot(String webUrl);
}

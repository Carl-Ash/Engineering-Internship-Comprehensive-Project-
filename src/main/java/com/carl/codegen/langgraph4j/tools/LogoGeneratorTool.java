package com.carl.codegen.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.carl.codegen.langgraph4j.state.ImageCategoryEnum;
import com.carl.codegen.langgraph4j.state.ImageResource;
import com.carl.codegen.manager.CosManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI Logo 生成工具 — 通过 DashScope 文生图生成后转存 COS 永久存储。
 */
@Slf4j
@Component
public class LogoGeneratorTool {

    private static final String IMAGE_SIZE = "512*512";
    private static final int GENERATE_COUNT = 1;
    private static final String PROMPT_TEMPLATE = "生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s";

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
    private String imageModel;

    @Resource
    private CosManager cosManager;

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        if (StrUtil.isBlank(description)) {
            return new ArrayList<>();
        }
        List<ImageResource> imageResources = new ArrayList<>();
        try {
            // 构建 Logo 设计提示词并调用 DashScope 文生图
            String logoPrompt = String.format(PROMPT_TEMPLATE, description);
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(logoPrompt)
                    .size(IMAGE_SIZE)
                    .n(GENERATE_COUNT)
                    .build();
            ImageSynthesisResult result = new ImageSynthesis().call(param);

            if (result == null || result.getOutput() == null || result.getOutput().getResults() == null) {
                return imageResources;
            }
            for (Map<String, String> imageResult : result.getOutput().getResults()) {
                String tempUrl = imageResult.get("url");
                if (StrUtil.isBlank(tempUrl)) {
                    continue;
                }
                // DashScope OSS 链接 24h 后失效，下载后转存到 COS 永久存储
                File tempFile = FileUtil.createTempFile("logo_", ".png", true);
                HttpUtil.downloadFile(tempUrl, tempFile);
                String cosKey = String.format("/logo/%s/%s", RandomUtil.randomString(5), tempFile.getName());
                String cosUrl = cosManager.uploadFile(cosKey, tempFile);
                FileUtil.del(tempFile);

                if (StrUtil.isNotBlank(cosUrl)) {
                    imageResources.add(ImageResource.builder()
                            .category(ImageCategoryEnum.LOGO)
                            .description(description)
                            .url(cosUrl)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return imageResources;
    }
}

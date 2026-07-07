package com.carl.codegen.langgraph4j.tools;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.carl.codegen.langgraph4j.state.ImageCategoryEnum;
import com.carl.codegen.langgraph4j.state.ImageResource;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * unDraw 插画搜索工具。
 */
@Slf4j
@Component
public class UndrawIllustrationTool {

    private static final String UNDRAW_API_URL = "https://undraw.co/_next/data/mMWmJSt23qpgo8cLTD_pB/search/%s.json?term=%s";
    private static final int SEARCH_COUNT = 10;

    @Tool("搜索插画图片，用于网站美化和装饰")
    public List<ImageResource> searchIllustrations(@P("搜索关键词") String query) {
        if (ObjUtil.isEmpty(query)) {
            return List.of();
        }
        List<ImageResource> imageResources = new ArrayList<>();
        String apiUrl = String.format(UNDRAW_API_URL, query, query);

        // 调用 unDraw 搜索接口
        try (HttpResponse response = HttpRequest.get(apiUrl).timeout(10000).execute()) {
            if (!response.isOk()) {
                return imageResources;
            }
            JSONObject result = JSONUtil.parseObj(response.body());
            JSONObject pageProps = result.getJSONObject("pageProps");
            if (pageProps == null) {
                return imageResources;
            }
            JSONArray illustrations = pageProps.getJSONArray("initialResults");
            if (illustrations == null || illustrations.isEmpty()) {
                return imageResources;
            }
            // 只取实际返回数量，不超过 SEARCH_COUNT
            int actualCount = Math.min(SEARCH_COUNT, illustrations.size());
            for (int i = 0; i < actualCount; i++) {
                JSONObject item = illustrations.getJSONObject(i);
                String title = item.getStr("title", "插画");
                String media = item.getStr("media", "");
                // 部分结果没有 media 字段，跳过
                if (StrUtil.isNotBlank(media)) {
                    imageResources.add(ImageResource.builder()
                            .category(ImageCategoryEnum.ILLUSTRATION)
                            .description(title)
                            .url(media)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("搜索插画失败: {}", e.getMessage(), e);
        }
        return imageResources;
    }
}

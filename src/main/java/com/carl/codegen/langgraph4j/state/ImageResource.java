package com.carl.codegen.langgraph4j.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图片资源，供下游阶段消费。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResource implements Serializable {

    // 用途分类
    private ImageCategoryEnum category;

    // 内容描述，用于生成替换 Prompt
    private String description;

    // 可访问的图片地址
    private String url;

    @Serial
    private static final long serialVersionUID = 1L;
}

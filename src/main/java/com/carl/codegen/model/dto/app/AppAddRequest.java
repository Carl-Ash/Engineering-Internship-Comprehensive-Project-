package com.carl.codegen.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用创建请求
 */
@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /**
     * 代码生成类型（html / multi_file）
     */
    private String codeGenType;

    /**
     * 可见范围：public / private，默认 public
     */
    private String visibility;

    private static final long serialVersionUID = 1L;
}

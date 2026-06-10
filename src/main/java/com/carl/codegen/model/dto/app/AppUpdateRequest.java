package com.carl.codegen.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用 更新请求。
 *
 * @author Carl
 */
@Data
public class AppUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    private static final long serialVersionUID = 1L;
}

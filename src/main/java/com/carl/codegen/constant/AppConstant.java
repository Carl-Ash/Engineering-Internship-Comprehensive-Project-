package com.carl.codegen.constant;

/**
 * 应用常量
 */
public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code-output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost:8080/api";

    /** 生成状态：未生成 */
    String GEN_STATUS_NONE = "none";
    /** 生成状态：生成中 */
    String GEN_STATUS_GENERATING = "generating";
    /** 生成状态：已完成 */
    String GEN_STATUS_COMPLETED = "completed";
    /** 生成状态：失败 */
    String GEN_STATUS_FAILED = "failed";

    /** 可见范围：公开 */
    String VISIBILITY_PUBLIC = "public";
    /** 可见范围：私有 */
    String VISIBILITY_PRIVATE = "private";

    /** 默认应用封面 */
    String DEFAULT_COVER = "";
}

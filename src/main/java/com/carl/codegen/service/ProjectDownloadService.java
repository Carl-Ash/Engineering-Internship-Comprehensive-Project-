package com.carl.codegen.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 项目下载服务
 *
 */
public interface ProjectDownloadService {

    /**
     * 将指定路径下的项目文件打包为ZIP并下载
     *
     * @param projectPath      项目目录路径
     * @param downloadFileName 下载文件名（不含扩展名）
     * @param response         HTTP响应
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}

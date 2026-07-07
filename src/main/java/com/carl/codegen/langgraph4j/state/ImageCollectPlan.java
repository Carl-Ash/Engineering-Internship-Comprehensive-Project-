package com.carl.codegen.langgraph4j.state;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 图片收集计划 — AI 分析用户需求后生成的结构化收集任务
 */
@Data
public class ImageCollectPlan implements Serializable {

    /** 内容图片搜索任务列表 */
    private List<ImageSearchTask> contentImageTasks;

    /** 插画图片搜索任务列表 */
    private List<IllustrationTask> illustrationTasks;

    /** 架构图生成任务列表 */
    private List<DiagramTask> diagramTasks;

    /** Logo 生成任务列表 */
    private List<LogoTask> logoTasks;

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 任务定义 ==========

    public record ImageSearchTask(String query) implements Serializable {}

    public record IllustrationTask(String query) implements Serializable {}

    public record DiagramTask(String mermaidCode, String description) implements Serializable {}

    public record LogoTask(String description) implements Serializable {}
}

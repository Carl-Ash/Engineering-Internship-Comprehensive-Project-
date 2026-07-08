package com.carl.codegen.controller;

import com.carl.codegen.langgraph4j.CodeGenWorkflow;
import com.carl.codegen.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 工作流 SSE 控制器 — 同步执行和流式输出两种模式
 */
@Slf4j
@RestController
@RequestMapping("/workflow")
public class WorkflowSseController {

    /**
     * 同步执行工作流
     */
    @PostMapping("/execute")
    public WorkflowContext executeWorkflow(@RequestParam String prompt) {
        log.info("收到同步工作流执行请求: {}", prompt);
        return new CodeGenWorkflow().executeWorkflow(prompt);
    }

    /**
     * Flux 流式执行工作流
     */
    @GetMapping(value = "/execute-flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> executeWorkflowWithFlux(@RequestParam String prompt) {
        log.info("收到 Flux 工作流执行请求: {}", prompt);
        return new CodeGenWorkflow().executeWorkflowWithFlux(prompt);
    }
}

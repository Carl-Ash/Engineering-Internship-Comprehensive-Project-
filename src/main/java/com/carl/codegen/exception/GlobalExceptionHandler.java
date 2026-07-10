package com.carl.codegen.exception;

import cn.hutool.json.JSONUtil;
import com.carl.codegen.common.BaseResponse;
import com.carl.codegen.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Map;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。SSE 流式请求的异常必须以 text/event-stream 格式写回，
     * 否则前端 EventSource 收到 JSON 响应会触发 onerror 而无法展示具体错误信息。
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        if (tryWriteSseError(e.getCode(), e.getMessage())) {
            return null; // SSE 已直接写入响应，无需再返回 JSON
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        if (tryWriteSseError(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误")) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /**
     * 将异常消息以 SSE 格式直接写入 HttpServletResponse，前端通过 EventSource 监听接收。
     * 使用自定义 business-error 事件类型而非浏览器内置 error 事件，
     * 避免与 EventSource 连接级别的 error 混淆。
     *
     * @return true 表示已是 SSE 请求且已处理，false 表示普通请求，走标准 JSON 响应
     */
    private boolean tryWriteSseError(int errorCode, String errorMessage) {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return false;
        }
        HttpServletRequest request = requestAttributes.getRequest();
        HttpServletResponse response = requestAttributes.getResponse();
        if (response == null) {
            return false;
        }
        // Accept 头是浏览器 EventSource 的标准标识；URI 兜底判断兼容未严格设置 Accept 的客户端
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        boolean isSseRequest = (accept != null && accept.contains("text/event-stream"))
                || uri.contains("/chat/gen/code");
        if (!isSseRequest) {
            return false;
        }
        try {
            // 先设置 SSE 响应头，再写入数据，确保浏览器按事件流解析
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            Map<String, Object> errorData = Map.of(
                    "error", true,
                    "code", errorCode,
                    "message", errorMessage
            );
            String errorJson = JSONUtil.toJsonStr(errorData);
            // 先发送业务错误事件，再发送结束事件，前端据此关闭 EventSource 连接
            response.getWriter().write("event: business-error\ndata: " + errorJson + "\n\n");
            response.getWriter().write("event: done\ndata: {}\n\n");
            response.getWriter().flush();
            return true;
        } catch (IOException | IllegalStateException ex) {
            // 写入失败：响应已提交或 IO 异常，已确认是 SSE 请求，避免再走 JSON 响应分支
            log.error("SSE 错误回写失败（响应可能已提交）", ex);
            return true;
        }
    }
}

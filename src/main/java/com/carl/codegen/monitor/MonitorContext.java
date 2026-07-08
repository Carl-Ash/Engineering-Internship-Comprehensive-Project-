package com.carl.codegen.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI调用监控上下文 — 携带当前请求的业务身份信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorContext implements Serializable {

    // 发起 AI 调用的用户 ID
    private String userId;

    // 被操作的目标应用 ID
    private String appId;

    @Serial
    private static final long serialVersionUID = 1L;
}

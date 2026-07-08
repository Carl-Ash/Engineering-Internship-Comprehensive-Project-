package com.carl.codegen.monitor;

/**
 * 监控上下文持有者 — 基于 ThreadLocal 在请求线程内传递监控上下文。
 */
public class MonitorContextHolder {

    /**
     * 监控上下文 ThreadLocal 持有者
     */
    private static final ThreadLocal<MonitorContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置监控上下文
     */
    public static void setContext(MonitorContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 获取监控上下文
     */
    public static MonitorContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 消除监控上下文
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }
}

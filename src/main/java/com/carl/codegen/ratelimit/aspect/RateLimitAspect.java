package com.carl.codegen.ratelimit.aspect;

import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.model.entity.User;
import com.carl.codegen.ratelimit.annotation.RateLimit;
import com.carl.codegen.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UserService userService;

    /**
     * 在标注了 @RateLimit 的方法执行前拦截，按用户维度校验限流。
     * 基于 Redisson 令牌桶算法实现：每个请求消耗 1 个令牌，令牌不足时抛出 429。
     */
    @Before("@annotation(rateLimit)")
    public void checkRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
        String rateLimitKey = buildRateLimitKey(rateLimit);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(rateLimitKey);
        // 必须设置过期，否则每个限流 key 在 Redis 中永不过期，长期运行内存持续增长
        rateLimiter.expire(Duration.ofHours(1));
        // trySetRate 仅首次创建时生效，已存在的 key 复用已有速率配置
        rateLimiter.trySetRate(RateType.OVERALL, rateLimit.rate(), rateLimit.rateInterval(), RateIntervalUnit.SECONDS);
        // 尝试获取 1 个令牌，失败表示已达限流阈值
        if (!rateLimiter.tryAcquire(1)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, rateLimit.message());
        }
    }

    /**
     * 按用户 ID 拼装限流 key，未登录时降级为 IP 限流防止绕过。
     */
    private String buildRateLimitKey(RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("rate_limit:");
        if (!rateLimit.key().isEmpty()) {
            keyBuilder.append(rateLimit.key()).append(":");
        }
        try {
            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                User loginUser = userService.getLoginUser(request);
                keyBuilder.append("user:").append(loginUser.getId());
            } else {
                // 拿不到请求上下文时降级为 IP 限流
                keyBuilder.append("ip:").append(getClientIP());
            }
        } catch (BusinessException e) {
            // 未登录用户降级为 IP 限流，防止绕过限制
            keyBuilder.append("ip:").append(getClientIP());
        }
        return keyBuilder.toString();
    }

    /**
     * 获取客户端真实 IP，优先从代理头 X-Forwarded-For 读取，
     * 依次兜底 X-Real-IP、RemoteAddr。
     * 多级代理时 X-Forwarded-For 包含多个 IP，取第一个（客户端真实 IP）。
     */
    private String getClientIP() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return "unknown";
        }
        HttpServletRequest request = requestAttributes.getRequest();
        // 依次尝试代理头，最后兜底 RemoteAddr
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}

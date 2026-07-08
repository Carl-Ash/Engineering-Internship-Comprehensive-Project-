package com.carl.codegen.ai;

import com.carl.codegen.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * AI代码生成类型智能路由服务测试
 *
 */
@Slf4j
@SpringBootTest
public class AiCodeGenRouterTest {

    @Resource
    private AiCodeGenRouter aiCodeGenRouter;

    @Test
    public void testRouteCodeGenType() {
        String userPrompt = "做一个简单的个人介绍页面";
        CodeGenTypeEnum result = aiCodeGenRouter.routeCodeGenType(userPrompt);
        log.info("用户需求: {} -> {}", userPrompt, result.getValue());

        userPrompt = "做一个公司官网，需要首页、关于我们、联系我们三个页面";
        result = aiCodeGenRouter.routeCodeGenType(userPrompt);
        log.info("用户需求: {} -> {}", userPrompt, result.getValue());

        userPrompt = "做一个电商管理系统，包含用户管理、商品管理、订单管理，需要路由和状态管理";
        result = aiCodeGenRouter.routeCodeGenType(userPrompt);
        log.info("用户需求: {} -> {}", userPrompt, result.getValue());
    }
}

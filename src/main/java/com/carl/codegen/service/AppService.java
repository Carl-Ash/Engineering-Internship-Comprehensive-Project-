package com.carl.codegen.service;

import com.carl.codegen.model.dto.app.AppQueryRequest;
import com.carl.codegen.model.entity.User;
import com.carl.codegen.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.carl.codegen.model.entity.App;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author Carl
 */
public interface AppService extends IService<App> {

    /**
     * 获取应用封装类
     *
     * @param app 应用
     * @return 应用封装类
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用封装类列表
     *
     * @param appList 应用列表
     * @return 应用封装类列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造应用查询条件
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 聊天生成代码
     * @param appId 应用 id
     * @param message 用户提示词
     * @param loginUser 登录用户
     * @return 实时透传的代码流
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 应用部署
     * @param appId 应用 id
     * @param loginUser 登录用户
     * @return 应用部署地址
     */
    String deployApp(Long appId, User loginUser);
}

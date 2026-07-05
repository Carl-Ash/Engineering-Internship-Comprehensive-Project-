package com.carl.codegen.service;

import com.carl.codegen.model.dto.app.AppAddRequest;
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
     * 创建应用
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 获取应用封装类
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用封装类列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造应用查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 聊天生成代码
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 应用部署
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 取消部署（下线）
     */
    String undeployApp(Long appId, User loginUser);

    /**
     * 删除应用并清理关联文件
     */
    void deleteAppWithCleanup(Long appId, App app);

    /**
     * 异步生成应用截图并更新封面
     */
    void generateAppScreenshotAsync(Long appId, String appUrl);
}

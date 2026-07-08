package com.carl.codegen.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.carl.codegen.ai.AiCodeGenRouterFactory;
import com.carl.codegen.config.CosClientConfig;
import com.carl.codegen.constant.AppConstant;
import com.carl.codegen.core.AiCodeGenFacade;
import com.carl.codegen.manager.CosManager;
import com.carl.codegen.monitor.MonitorContext;
import com.carl.codegen.monitor.MonitorContextHolder;
import com.carl.codegen.core.builder.VueBuilder;
import com.carl.codegen.core.handler.StreamHandlerExecutor;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.exception.ThrowUtils;
import com.carl.codegen.model.dto.app.AppAddRequest;
import com.carl.codegen.model.dto.app.AppQueryRequest;
import com.carl.codegen.model.entity.User;
import com.carl.codegen.model.enums.ChatHistoryMessageTypeEnum;
import com.carl.codegen.model.enums.CodeGenTypeEnum;
import com.carl.codegen.model.vo.AppVO;
import com.carl.codegen.model.vo.UserVO;
import com.carl.codegen.service.ChatHistoryService;
import com.carl.codegen.service.ScreenshotService;
import com.carl.codegen.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.carl.codegen.model.entity.App;
import com.carl.codegen.mapper.AppMapper;
import com.carl.codegen.service.AppService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserService userService;
    @Resource
    private AiCodeGenFacade aiCodeGenFacade;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private VueBuilder vueBuilder;
    @Resource
    private ScreenshotService screenshotService;
    @Resource
    private CosManager cosManager;
    @Resource
    private CosClientConfig cosClientConfig;
    @Resource
    private AiCodeGenRouterFactory aiCodeGenRouterFactory;

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        App app = new App();
        app.setInitPrompt(initPrompt);
        // 使用 AI 智能选择代码生成类型（多例模式）
        CodeGenTypeEnum selectedCodeGenType = aiCodeGenRouterFactory.createAiCodeGenRouter().routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // AI 生成应用名称：取 prompt 前 30 字
        String autoName = initPrompt.length() > 30 ? initPrompt.substring(0, 30) : initPrompt;
        app.setAppName(autoName);
        app.setUserId(loginUser.getId());
        app.setGenStatus(AppConstant.GEN_STATUS_NONE);
        app.setVersion(0);
        app.setVisibility(StrUtil.isNotBlank(appAddRequest.getVisibility())
                ? appAddRequest.getVisibility()
                : AppConstant.VISIBILITY_PUBLIC);
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String genStatus = appQueryRequest.getGenStatus();
        String visibility = appQueryRequest.getVisibility();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .eq("genStatus", genStatus)
                .eq("visibility", visibility)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        // 设置生成状态为 generating
        App statusUpdate = new App();
        statusUpdate.setId(appId);
        statusUpdate.setGenStatus(AppConstant.GEN_STATUS_GENERATING);
        this.updateById(statusUpdate);
        // 保存对话历史到数据库
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 设置监控上下文
        MonitorContextHolder.setContext(
                MonitorContext.builder()
                        .userId(loginUser.getId().toString())
                        .appId(appId.toString())
                        .build()
        );
        // 调用 AI 生成代码
        Flux<String> codeFlux = aiCodeGenFacade.genAndSaveStream(message, codeGenTypeEnum, appId);
        // 收集AI响应内容，并完成后保存到数据库对话历史
        return streamHandlerExecutor.execute(codeFlux, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                // 无论流正常完成、出错还是被取消，都要清除 ThreadLocal 避免内存泄漏
                .doFinally(signalType -> MonitorContextHolder.clearContext());
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成应用代码");
        }
        // Vue 项目特殊处理，执行 Vue 项目构建命令
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE3) {
            boolean buildResult = vueBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildResult, ErrorCode.OPERATION_ERROR, "Vue 项目构建失败，请重试");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 复制 dist 目录到部署目录
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 构建应用访问URL
        String appDeployUrl = String.format("%s/deploy/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    /**
     * 取消部署（下线）
     */
    public String undeployApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该应用");
        }
        String deployKey = app.getDeployKey();
        if (StrUtil.isNotBlank(deployKey)) {
            // 删除部署目录
            String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
            try {
                FileUtil.del(deployDirPath);
            } catch (Exception e) {
                // 删除失败不阻塞流程
            }
            // 清除 deployKey 和部署时间
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setDeployKey(null);
            updateApp.setDeployedTime(null);
            this.updateById(updateApp);
        }
        return "已下线";
    }

    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        Thread.startVirtualThread(() -> {
            try {
                String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
                if (StrUtil.isBlank(screenshotUrl)) {
                    screenshotUrl = AppConstant.DEFAULT_COVER;
                }
                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCover(screenshotUrl);
                boolean updated = this.updateById(updateApp);
                ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
            } catch (Exception e) {
                log.error("异步生成截图失败, appId: {}", appId, e);
                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCover(AppConstant.DEFAULT_COVER);
                this.updateById(updateApp);
            }
        });
    }

    /**
     * 删除应用并清理关联文件
     */
    public void deleteAppWithCleanup(Long appId, App app) {
        // 清理COS封面
        String cover = app.getCover();
        if (StrUtil.isNotBlank(cover)) {
            try {
                String cosKey = cover.substring(cosClientConfig.getHost().length());
                cosManager.deleteObject(cosKey);
            } catch (Exception e) {
                // 删除失败不阻塞
            }
        }
        // 清理代码生成目录
        String codeGenType = app.getCodeGenType();
        if (StrUtil.isNotBlank(codeGenType)) {
            String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + codeGenType + "_" + appId;
            try {
                FileUtil.del(sourceDirPath);
            } catch (Exception e) {
                // 删除失败不阻塞
            }
        }
        // 清理部署目录
        String deployKey = app.getDeployKey();
        if (StrUtil.isNotBlank(deployKey)) {
            String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
            try {
                FileUtil.del(deployDirPath);
            } catch (Exception e) {
                // 删除失败不阻塞
            }
        }
    }

    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否删除成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) return false;
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) return false;
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("删除应用关联对话历史失败", e.getMessage());
        }

        return super.removeById(id);
    }
}

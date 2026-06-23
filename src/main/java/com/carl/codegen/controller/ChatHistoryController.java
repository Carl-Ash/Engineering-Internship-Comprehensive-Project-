package com.carl.codegen.controller;

import cn.hutool.core.util.StrUtil;
import com.carl.codegen.annotation.AuthCheck;
import com.carl.codegen.common.BaseResponse;
import com.carl.codegen.common.DeleteRequest;
import com.carl.codegen.common.ResultUtils;
import com.carl.codegen.constant.UserConstant;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.exception.ThrowUtils;
import com.carl.codegen.model.dto.chathistory.ChatHistoryQueryRequest;
import com.carl.codegen.model.entity.ChatHistory;
import com.carl.codegen.model.entity.User;
import com.carl.codegen.service.AppService;
import com.carl.codegen.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import com.carl.codegen.service.ChatHistoryService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 对话历史 控制层。
 *
 * @author Carl
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;

    @Resource
    private AppService appService;

    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @param request        请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistory>> listAppChatHistory(@PathVariable Long appId,
                                                              @RequestParam(defaultValue = "10") int pageSize,
                                                              @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistory> result = chatHistoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 导出对话历史为 Markdown 文件
     */
    @GetMapping("/app/{appId}/export")
    public void exportChatHistory(@PathVariable Long appId,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {
        User loginUser = userService.getLoginUser(request);
        String appName = appService.getById(appId).getAppName();
        String markdown = chatHistoryService.exportToMarkdown(appId, appName, loginUser);
        if (StrUtil.isBlank(markdown)) {
            markdown = "# 对话历史 - " + appName + "\n\n> 暂无对话记录\n";
        }
        String fileName = (appName != null ? appName : "对话历史") + ".md";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("text/markdown; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
        response.setCharacterEncoding("UTF-8");
        try (OutputStream out = response.getOutputStream()) {
            out.write(markdown.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    /**
     * 管理员分页查询所有对话历史
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 对话历史分页
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> listAllChatHistoryByPageForAdmin(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = chatHistoryQueryRequest.getPageNum();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        // 查询数据
        QueryWrapper queryWrapper = chatHistoryService.getQueryWrapper(chatHistoryQueryRequest);
        Page<ChatHistory> result = chatHistoryService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(result);
    }

    /**
     * 管理员删除单条对话历史
     *
     * @param deleteRequest 删除请求
     * @return 是否删除成功
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteChatHistoryByAdmin(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        boolean result = chatHistoryService.deleteChatHistoryById(deleteRequest.getId());
        return ResultUtils.success(result);
    }

}

package com.carl.codegen.service;

import com.carl.codegen.model.dto.user.UserQueryRequest;
import com.carl.codegen.model.vo.LoginUserVO;
import com.carl.codegen.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.carl.codegen.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author Carl
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 加密密码
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * 登录用户 vo
     *
     * @param user 用户
     * @return 登录用户 vo
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取登录用户
     *
     * @param request
     * @return 登录用户
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取脱敏后的用户信息（分页）
     *
     * @param userList 用户列表
     * @return
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取脱敏后的用户信息
     *
     * @param user 用户信息
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 根据查询条件构造数据查询参数
     *
     * @return
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

}

package com.carl.codegen.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户个人资料更新请求（不包含角色字段，防止权限提升）
 */
@Data
public class UserUpdateProfileRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    private static final long serialVersionUID = 1L;
}

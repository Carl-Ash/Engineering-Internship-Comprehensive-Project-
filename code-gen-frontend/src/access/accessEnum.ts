/**
 * 权限枚举（角色级别）
 */
export const ACCESS_ENUM = {
  NOT_LOGIN: 'notLogin',
  USER: 'user',
  ADMIN: 'admin',
  SUPER_ADMIN: 'superAdmin',
} as const

/**
 * 角色级别数值：高级别自动拥有低级别权限
 */
export const ROLE_LEVEL: Record<string, number> = {
  notLogin: -1,
  user: 0,
  admin: 50,
  superAdmin: 999,
}

/**
 * 权限码（细粒度控制）
 * 格式：resource:action
 */
export const PERMISSION_CODE = {
  USER_VIEW: 'user:view',
  USER_CREATE: 'user:create',
  USER_UPDATE: 'user:update',
  USER_DELETE: 'user:delete',
  USER_ROLE_MANAGE: 'user:role:manage',
  DASHBOARD_VIEW: 'dashboard:view',
} as const

/**
 * 角色 → 权限码映射
 */
export const ROLE_PERMISSIONS: Record<string, string[]> = {
  superAdmin: Object.values(PERMISSION_CODE),
  admin: [
    PERMISSION_CODE.USER_VIEW,
    PERMISSION_CODE.USER_CREATE,
    PERMISSION_CODE.USER_UPDATE,
    PERMISSION_CODE.USER_DELETE,
    PERMISSION_CODE.DASHBOARD_VIEW,
  ],
  user: [PERMISSION_CODE.DASHBOARD_VIEW, PERMISSION_CODE.USER_VIEW],
}

export default ACCESS_ENUM

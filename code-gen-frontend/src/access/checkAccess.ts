import { ACCESS_ENUM, ROLE_LEVEL, ROLE_PERMISSIONS } from './accessEnum'

const permissionCache = new Map<string, boolean>()

export function clearPermissionCache() {
  permissionCache.clear()
}

function getUserPermissions(loginUser: any): string[] {
  if (loginUser?.permissions && Array.isArray(loginUser.permissions)) {
    return loginUser.permissions
  }
  const role = loginUser?.userRole ?? ACCESS_ENUM.NOT_LOGIN
  return ROLE_PERMISSIONS[role] ?? []
}

/**
 * 检查权限（支持角色级别继承）
 * @param loginUser 当前登录用户
 * @param needAccess 需要的权限：角色(如 'admin') | 权限码(如 'user:view') | 权限码数组
 */
const checkAccess = (
  loginUser: any,
  needAccess: string | string[] = ACCESS_ENUM.NOT_LOGIN,
): boolean => {
  const userRole = loginUser?.userRole ?? ACCESS_ENUM.NOT_LOGIN

  if (needAccess === ACCESS_ENUM.NOT_LOGIN || (Array.isArray(needAccess) && needAccess.length === 0)) {
    return true
  }

  // 缓存 key
  const isArray = Array.isArray(needAccess)
  const cacheKey = `${loginUser?.id ?? 'anon'}_${isArray ? (needAccess as string[]).slice().sort().join(',') : needAccess}`
  if (permissionCache.has(cacheKey)) return permissionCache.get(cacheKey)!

  let result: boolean

  if (isArray) {
    const permissions = getUserPermissions(loginUser)
    result = (needAccess as string[]).every((p) => permissions.includes(p))
  } else if (Object.values(ACCESS_ENUM).includes(needAccess as any)) {
    // 角色检查：使用级别比较（高级别自动包含低级别）
    const userLevel = ROLE_LEVEL[userRole] ?? -1
    const needLevel = ROLE_LEVEL[needAccess] ?? -1
    result = userLevel >= needLevel
  } else {
    // 权限码检查
    const permissions = getUserPermissions(loginUser)
    result = permissions.includes(needAccess)
  }

  permissionCache.set(cacheKey, result)
  return result
}

export default checkAccess

import { useLoginUserStore } from '@/stores/loginUser'
import checkAccess from '@/access/checkAccess'

/**
 * 权限判断组合式函数
 */
export function usePermission() {
  const loginUserStore = useLoginUserStore()

  /**
   * 判断当前用户是否有指定权限
   * @param permission 权限码 / 角色 / 权限码数组（需全部满足）
   */
  function hasPermission(permission: string | string[]): boolean {
    return checkAccess(loginUserStore.loginUser, permission)
  }

  /**
   * 判断当前用户是否有任一权限（数组中有任意一个满足即可）
   */
  function hasAnyPermission(permissions: string[]): boolean {
    return permissions.some((p) => checkAccess(loginUserStore.loginUser, p))
  }

  return { hasPermission, hasAnyPermission }
}

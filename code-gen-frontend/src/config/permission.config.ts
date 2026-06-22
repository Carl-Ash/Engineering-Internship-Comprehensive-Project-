import { h } from 'vue'
import { HomeOutlined, AppstoreOutlined } from '@ant-design/icons-vue'
import { ACCESS_ENUM } from '@/access/accessEnum'

/**
 * 路由权限配置项
 */
export interface RoutePermission {
  /** 路由路径 */
  path: string
  /** 所需权限（权限码或角色），不填则公开 */
  permission?: string | string[]
  /** 是否在菜单中隐藏 */
  hidden?: boolean
  /** 菜单排序 */
  order?: number
  /** 菜单图标 */
  icon?: () => any
  /** 菜单名称 */
  label?: string
}

/**
 * 集中管理所有路由权限配置
 * 新增页面只需在这里添加一行，菜单自动生成、权限自动校验
 */
export const routePermissions: RoutePermission[] = [
  {
    path: '/',
    label: '首页',
    icon: () => h(HomeOutlined),
    order: 1,
  },
  {
    path: '/admin/userManage',
    label: '用户管理',
    permission: ACCESS_ENUM.ADMIN,
    order: 10,
  },
  {
    path: '/admin/appManage',
    label: '应用管理',
    icon: () => h(AppstoreOutlined),
    permission: ACCESS_ENUM.USER,
    order: 20,
  },
]

/**
 * 根据当前用户权限，获取可见的菜单项
 */
export function getVisibleMenus(
  checkFn: (permission: string | string[]) => boolean,
): { key: string; icon?: any; label: string; title: string }[] {
  return routePermissions
    .filter((item) => {
      if (item.hidden) return false
      if (!item.permission) return true
      return checkFn(item.permission)
    })
    .sort((a, b) => (a.order ?? 99) - (b.order ?? 99))
    .map((item) => ({
      key: item.path,
      icon: item.icon?.(),
      label: item.label ?? item.path,
      title: item.label ?? item.path,
    }))
}

/**
 * 获取路由路径对应的权限配置
 */
export function getRoutePermission(path: string): string | string[] | undefined {
  return routePermissions.find((r) => r.path === path)?.permission
}

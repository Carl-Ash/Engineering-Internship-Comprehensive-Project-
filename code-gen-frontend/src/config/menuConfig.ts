import { h } from 'vue'
import { HomeOutlined } from '@ant-design/icons-vue'
import { PERMISSION_CODE } from '@/access/accessEnum'

export interface MenuItemConfig {
  key: string
  icon?: () => any
  label: string
  title: string
  /** 需要的权限（权限码或角色），不填则公开 */
  permission?: string | string[]
}

/**
 * 菜单配置（单文件维护所有菜单 + 所需权限）
 */
export const menuConfig: MenuItemConfig[] = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '首页',
    title: '首页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
    permission: PERMISSION_CODE.USER_VIEW,
  },
]

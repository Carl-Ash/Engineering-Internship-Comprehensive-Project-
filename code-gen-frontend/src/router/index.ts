import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../pages/HomePage.vue'
import UserLoginPage from '../pages/user/UserLoginPage.vue'
import UserRegisterPage from '../pages/user/UserRegisterPage.vue'
import UserSettingsPage from '../pages/user/UserSettingsPage.vue'
import UserManagePage from '../pages/admin/UserManagePage.vue'
import NoAuthPage from '../pages/NoAuthPage.vue'
import { ACCESS_ENUM, PERMISSION_CODE } from '@/access/accessEnum'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: '首页',
      component: HomePage,
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLoginPage,
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegisterPage,
    },
    {
      path: '/user/settings',
      name: '个人设置',
      component: UserSettingsPage,
      meta: {
        permission: ACCESS_ENUM.USER,
      },
    },
    {
      path: '/admin/userManage',
      name: '用户管理',
      component: UserManagePage,
      meta: {
        permission: PERMISSION_CODE.USER_VIEW,
      },
    },
    {
      path: '/noAuth',
      name: '无权限',
      component: NoAuthPage,
    },
  ],
})

export default router

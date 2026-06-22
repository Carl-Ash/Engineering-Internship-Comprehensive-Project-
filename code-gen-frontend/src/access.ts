import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'
import router from '@/router'

let firstFetchLoginUser = true

router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser

  if (firstFetchLoginUser) {
    try {
      await loginUserStore.fetchLoginUser()
      loginUser = loginUserStore.loginUser
    } catch {
      // 后端未启动时允许免登录访问
    }
    firstFetchLoginUser = false
  }

  const toUrl = to.fullPath

  // 用户管理：仅管理员和超级管理员
  if (toUrl.startsWith('/admin/userManage')) {
    if (!loginUser || (loginUser.userRole !== 'admin' && loginUser.userRole !== 'superAdmin')) {
      message.error('没有权限')
      next(`/user/login?redirect=${to.fullPath}`)
      return
    }
  }

  // 应用管理、对话、编辑：需要登录
  if (toUrl.startsWith('/admin/appManage') || toUrl.startsWith('/app/chat') || toUrl.startsWith('/app/edit')) {
    if (!loginUser || !loginUser.userRole || loginUser.userRole === 'notLogin') {
      next(`/user/login?redirect=${to.fullPath}`)
      return
    }
  }

  next()
})

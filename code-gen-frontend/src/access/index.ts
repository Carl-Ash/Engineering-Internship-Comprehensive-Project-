import router from '@/router'
import { useLoginUserStore } from '@/stores/loginUser'
import { ACCESS_ENUM } from './accessEnum'
import checkAccess from './checkAccess'

router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser

  // 首次加载，自动获取登录用户信息
  if (!loginUser || !loginUser.userRole) {
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
  }

  // 优先使用 permission（权限码），兼容旧的 access（角色）
  const needAccess = (to.meta?.permission as string | string[]) ?? (to.meta?.access as string) ?? ACCESS_ENUM.NOT_LOGIN

  // 无需权限，直接放行
  if (
    needAccess === ACCESS_ENUM.NOT_LOGIN ||
    (Array.isArray(needAccess) && needAccess.length === 0)
  ) {
    next()
    return
  }

  // 需要登录但未登录，跳转登录页
  if (!loginUser || !loginUser.userRole || loginUser.userRole === ACCESS_ENUM.NOT_LOGIN) {
    next(`/user/login?redirect=${to.fullPath}`)
    return
  }

  // 已登录但权限不足
  if (!checkAccess(loginUser, needAccess)) {
    next('/noAuth')
    return
  }

  next()
})

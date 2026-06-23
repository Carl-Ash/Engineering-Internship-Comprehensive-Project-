<template>
  <a-layout-header class="header">
    <div class="header-content">
      <!-- 左侧：Logo和标题 -->
      <RouterLink to="/" class="header-left">
        <img class="logo" src="@/assets/logo.png" alt="Logo" />
        <h1 class="site-title">AI 应用生成平台</h1>
      </RouterLink>

      <!-- 中间：导航菜单 -->
      <nav class="header-menu">
        <RouterLink
          v-for="item in menuItems"
          :key="item.key"
          :to="item.key as string"
          class="menu-item"
          :class="{ active: selectedKeys.includes(item.key as string) }"
        >
          <component v-if="item.icon" :is="item.icon" class="menu-icon" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <!-- 右侧：用户操作区域 -->
      <div class="header-right">
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown>
              <a-space>
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                {{ loginUserStore.loginUser.userName ?? '无名' }}
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="doSettings">
                    <SettingOutlined />
                    个人信息
                  </a-menu-item>
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </div>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import { LogoutOutlined, HomeOutlined, SettingOutlined } from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()

const selectedKeys = ref<string[]>(['/'])

router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

interface MenuItem {
  key: string
  icon?: typeof HomeOutlined
  label: string
  title: string
}

const originItems: MenuItem[] = [
  {
    key: '/',
    icon: HomeOutlined,
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
    title: '应用管理',
  },
]

const filterMenus = (menus: MenuItem[]) => {
  return menus.filter((menu) => {
    if (menu.key === '/admin/userManage') {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || (loginUser.userRole !== 'admin' && loginUser.userRole !== 'superAdmin')) {
        return false
      }
    }
    return true
  })
}

const menuItems = computed(() => filterMenus(originItems))

const doSettings = () => {
  router.push('/user/settings')
}

const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.header {
  background: var(--bg-header);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  padding: 0 32px;
  border-bottom: 1px solid var(--border-color);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  height: 64px;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  height: 100%;
  width: 100%;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  text-decoration: none;
  margin-right: 40px;
}

.logo {
  height: 40px;
  width: 40px;
  border-radius: 10px;
  transition: transform 0.3s;
}

.logo:hover {
  transform: scale(1.08) rotate(-5deg);
}

.site-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.3px;
}

.header-menu {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  text-decoration: none;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
  white-space: nowrap;
}

.menu-item:hover {
  background: rgba(var(--primary-color-rgb), 0.06);
  color: var(--text-color);
}

.menu-item.active {
  color: var(--primary-color);
  font-weight: 600;
  background: rgba(var(--primary-color-rgb), 0.08);
}

.menu-icon {
  font-size: 14px;
}

.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
}

.user-login-status :deep(.ant-btn-primary) {
  border-radius: 8px;
  font-weight: 500;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.25);
  transition: all 0.3s;
}

.user-login-status :deep(.ant-btn-primary):hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.35);
}
</style>

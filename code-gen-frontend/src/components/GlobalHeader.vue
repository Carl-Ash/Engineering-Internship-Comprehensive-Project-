<template>
  <a-layout-header class="header">
    <a-row :wrap="false">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.svg" alt="Logo" />
            <h1 class="site-title">代码生成</h1>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col>
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown>
              <a-space class="user-dropdown-trigger">
                <a-avatar :size="32" :src="loginUserStore.loginUser.userAvatar">
                  <template #icon><UserOutlined /></template>
                </a-avatar>
                <span>{{ loginUserStore.loginUser.userName ?? '无名' }}</span>
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="settings" @click="goToSettings">
                    <SettingOutlined />
                    个人设置
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="logout" @click="doLogout">
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
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import type { MenuProps } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import {
  LogoutOutlined,
  SettingOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController'
import checkAccess from '@/access/checkAccess'
import { getVisibleMenus } from '@/config/permission.config'

const loginUserStore = useLoginUserStore()
const router = useRouter()

const selectedKeys = ref<string[]>(['/'])

router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

// 从 permission.config 生成菜单
const menuItems = computed(() => {
  return getVisibleMenus((perm) => checkAccess(loginUserStore.loginUser, perm))
})

const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  if (key.startsWith('/')) {
    router.push(key)
  }
}

const goToSettings = () => {
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
  background: var(--bg-header, #fff);
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.09);
  position: sticky;
  top: 0;
  z-index: 10;
  color: var(--text-color, #1a1a1a);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  height: 48px;
  width: 48px;
}

.site-title {
  margin: 0;
  font-size: 18px;
  color: var(--primary-color, #1890ff);
}

.header :deep(.ant-menu-horizontal) {
  border-bottom: none !important;
  background: transparent !important;
}

.header :deep(.ant-menu-item) {
  color: var(--text-color, #1a1a1a) !important;
}

.header :deep(.ant-menu-item:hover) {
  color: var(--primary-color, #1890ff) !important;
}

.header :deep(.ant-menu-item-selected) {
  color: var(--primary-color, #1890ff) !important;
}

.user-dropdown-trigger {
  cursor: pointer;
  color: var(--text-color, #1a1a1a);
}

@media (max-width: 768px) {
  .site-title {
    display: none;
  }
}
</style>

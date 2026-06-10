<template>
  <a-layout class="basic-layout">
    <!-- 顶部导航栏 -->
    <GlobalHeader />

    <!-- 主要内容区域 -->
    <a-layout-content class="main-content" :class="{ 'fullscreen-content': isLoginRoute }">
      <router-view v-slot="{ Component }">
        <div v-if="isLoginPage(Component)" class="full-screen-center">
          <component :is="Component" />
        </div>
        <div v-else class="page-container">
          <component :is="Component" />
        </div>
      </router-view>
    </a-layout-content>

    <!-- 底部版权信息 -->
    <GlobalFooter />
  </a-layout>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import { useRoute } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()
const isLoginRoute = computed(() => route.path === '/user/login' || route.path === '/user/register')

const isLoginPage = (Component: any) => {
  return Component?.type?.name === 'UserLoginPage' || Component?.type?.name === 'UserRegisterPage'
}
</script>

<style scoped>
.basic-layout {
  background: var(--bg-page, #f5f5f5);
  min-height: 100vh;
}

.main-content {
  min-height: calc(100vh - 64px - 60px);
}

.fullscreen-content {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.page-container {
  max-width: 1200px;
  margin: 16px auto;
  padding: 0 16px;
}

.full-screen-center {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}
</style>

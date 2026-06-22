<template>
  <div class="no-auth-page">
    <div class="no-auth-content">
      <a-result
        status="403"
        title="403"
        :sub-title="`抱歉，您没有权限访问${routePath}。如确需访问，请联系管理员。`"
      >
        <template #extra>
          <a-space>
            <a-button type="primary" @click="goHome">返回首页</a-button>
            <a-button @click="goBack">返回上一页</a-button>
          </a-space>
        </template>
      </a-result>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const routePath = computed(() => {
  const redirect = route.query.redirect as string
  return redirect ? `「${redirect}」` : '该页面'
})

const goHome = () => {
  router.push('/')
}

const goBack = () => {
  router.back()
}
</script>

<style scoped>
.no-auth-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
  background: var(--bg-page);
}

.no-auth-content {
  padding: 48px;
  background: var(--bg-card);
  border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
</style>

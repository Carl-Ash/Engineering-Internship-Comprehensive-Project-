<template>
  <div id="userLoginPage">
    <div class="login-card">
      <div class="card-header">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 11.068L17.625 13.5l-.634-2.432a3.75 3.75 0 00-2.059-2.059L12.5 8.375l2.432-.634a3.75 3.75 0 002.059-2.059L17.625 3.25l.634 2.432a3.75 3.75 0 002.059 2.059L22.75 8.375l-2.432.634a3.75 3.75 0 00-2.059 2.059z"/>
          </svg>
        </div>
        <h2 class="title">AI 应用生成平台</h2>
        <p class="desc">欢迎回来，请登录您的账号</p>
      </div>
      <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
        <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input v-model:value="formState.userAccount" placeholder="请输入账号" size="large" />
        </a-form-item>
        <a-form-item
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码长度不能小于 8 位' },
          ]"
        >
          <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" size="large" />
        </a-form-item>
        <div class="tips">
          没有账号？
          <RouterLink to="/user/register">去注册</RouterLink>
        </div>
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" style="width: 100%" :loading="loading">登录</a-button>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const router = useRouter()
const loginUserStore = useLoginUserStore()
const loading = ref(false)

const handleSubmit = async (values: any) => {
  loading.value = true
  try {
    const res = await userLogin(values)
    if (res.data.code === 0 && res.data.data) {
      await loginUserStore.fetchLoginUser()
      message.success('登录成功')
      router.push({
        path: '/',
        replace: true,
      })
    } else {
      message.error('登录失败，' + res.data.message)
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
#userLoginPage {
  min-height: calc(100vh - 64px - 70px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    var(--bg-page),
    radial-gradient(circle at 20% 80%, rgba(var(--primary-color-rgb), 0.08) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(139, 92, 246, 0.08) 0%, transparent 50%);
}

.login-card {
  width: 100%;
  max-width: 420px;
  background: var(--bg-card);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 40px 32px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.12), 0 0 0 1px rgba(var(--primary-color-rgb), 0.1);
  border: 1px solid var(--border-color);
}

.card-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  color: #fff;
  margin-bottom: 16px;
  box-shadow: 0 8px 24px rgba(59, 130, 246, 0.3);
}

.title {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
  color: var(--text-color);
}

.desc {
  color: var(--text-secondary);
  margin: 0;
  font-size: 15px;
}

.tips {
  text-align: right;
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 16px;
}

.tips a {
  color: var(--primary-color);
}

:deep(.ant-input-lg),
:deep(.ant-input-password) {
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  transition: all 0.3s;
}

:deep(.ant-input-lg):focus,
:deep(.ant-input-password .ant-input):focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(var(--primary-color-rgb), 0.1);
  background: var(--bg-card);
}

:deep(.ant-btn-primary.ant-btn-lg) {
  border-radius: 12px;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 8px 24px rgba(59, 130, 246, 0.3);
  transition: all 0.3s;
}

:deep(.ant-btn-primary.ant-btn-lg):hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(59, 130, 246, 0.4);
}
</style>

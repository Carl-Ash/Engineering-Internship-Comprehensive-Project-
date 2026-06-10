<template>
  <div class="login-container">
    <div class="login-wrapper">
      <div class="login-card">
        <div class="logo-section">
          <div class="logo-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L2 7l10 5 10-5-10-5z" />
              <path d="M2 17l10 5 10-5" />
              <path d="M2 12l10 5 10-5" />
            </svg>
          </div>
          <h2 class="title">AI 应用生成</h2>
          <div class="desc">不写一行代码，生成完整应用</div>
        </div>

        <a-form
          :model="formState"
          name="normal_login"
          class="login-form"
          @finish="handleSubmit"
          @finishFailed="handleFinishFailed"
        >
          <a-form-item
            label="用户账号"
            name="userAccount"
            :rules="[{ required: true, message: '请输入账号!' }]"
          >
            <a-input v-model:value="formState.userAccount" placeholder="请输入账号" class="input-field">
              <template #prefix>
                <UserOutlined class="site-form-item-icon" />
              </template>
            </a-input>
          </a-form-item>

          <a-form-item
            label="用户密码"
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码!' },
              { min: 8, message: '密码不能小于 8 位!' },
            ]"
          >
            <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" class="input-field">
              <template #prefix>
                <LockOutlined class="site-form-item-icon" />
              </template>
            </a-input-password>
          </a-form-item>

          <a-form-item>
            <div class="remember-register">
              <a-form-item name="remember" no-style>
                <a-checkbox v-model:checked="formState.remember" class="checkbox-custom">记住密码</a-checkbox>
              </a-form-item>
              <a href="/user/register" class="register-link">去注册</a>
            </div>
          </a-form-item>

          <a-form-item>
            <a-button :disabled="disabled" type="primary" html-type="submit" class="login-form-button">
              <span class="btn-text">登 录</span>
            </a-button>
          </a-form-item>
        </a-form>

        <div class="footer-tips">
          <span>登录即表示同意</span>
          <a href="#" class="link">服务条款</a>
          <span>和</span>
          <a href="#" class="link">隐私政策</a>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
defineOptions({ name: 'UserLoginPage' })

import { reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { userLogin } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'

const router = useRouter()
const loginUserStore = useLoginUserStore()

interface FormState {
  userAccount: string
  userPassword: string
  remember: boolean
}

const formState = reactive<FormState>({
  userAccount: '',
  userPassword: '',
  remember: false,
})

const handleSubmit = async (values: any) => {
  if (!formState.remember) {
    localStorage.removeItem('remember_pwd_' + formState.userAccount)
  }
  const res = await userLogin(values)
  if (res.data.code === 0 && res.data.data) {
    if (formState.remember) {
      localStorage.setItem('remember_account', formState.userAccount)
      localStorage.setItem('remember_pwd_' + formState.userAccount, formState.userPassword)
    }
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}

const handleFinishFailed = (errorInfo: any) => {
  console.log('表单验证失败:', errorInfo)
}

const disabled = computed(() => {
  return !(formState.userAccount && formState.userPassword)
})

onMounted(() => {
  const savedAccount = localStorage.getItem('remember_account')
  if (savedAccount) {
    formState.userAccount = savedAccount
    const savedPassword = localStorage.getItem('remember_pwd_' + savedAccount)
    if (savedPassword) {
      formState.userPassword = savedPassword
      formState.remember = true
    }
  }
})
</script>
<style scoped>
.login-container {
  width: 700px;
  height: 550px;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  border-radius: 32px;
}

.login-wrapper {
  width: 100%;
  max-width: 550px;
}

.login-card {
  width: auto;
  background: linear-gradient(145deg, #ffffff 0%, #f8f9fa 100%);
  border-radius: 24px;
  padding: 32px 32px 24px;
  box-shadow: 
    0 20px 60px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.8) inset,
    0 1px 2px rgba(255, 255, 255, 0.6) inset;
  border: 1px solid rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(20px);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.login-card::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: linear-gradient(
    45deg,
    transparent 30%,
    rgba(102, 126, 234, 0.05) 50%,
    transparent 70%
  );
  animation: shimmer 8s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    transform: translateX(-100%) rotate(45deg);
  }
  100% {
    transform: translateX(100%) rotate(45deg);
  }
}

.logo-section {
  text-align: center;
  margin-bottom: 16px;
}

.logo-icon {
  width: 52px;
  height: 52px;
  margin: 0 auto 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 28px;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.4);
}

.logo-icon svg {
  width: 26px;
  height: 26px;
}

.title {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 4px;
  letter-spacing: -0.5px;
}

.desc {
  font-size: 13px;
  color: #888;
}

.login-form {
  margin-top: 8px;
}

:deep(.ant-form-item) {
  margin-bottom: 16px;
}

.input-field {
  border-radius: 12px;
  height: 42px;
  font-size: 15px;
  transition: all 0.3s ease;
}

.input-field:focus {
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.site-form-item-icon {
  color: #ccc;
  font-size: 16px;
}

.remember-register {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.checkbox-custom {
  font-size: 14px;
  color: #666;
}

.register-link {
  color: #667eea;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
}

.register-link:hover {
  text-decoration: underline;
}

.login-form-button {
  width: 100%;
  height: 44px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
  transition: all 0.3s ease;
}

.login-form-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
}

.login-form-button:active:not(:disabled) {
  transform: translateY(0);
}

.login-form-button:disabled {
  background: #ddd;
  box-shadow: none;
}

.btn-text {
  letter-spacing: 4px;
}

.footer-tips {
  text-align: center;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #eee;
  font-size: 12px;
  color: #999;
}

.footer-tips .link {
  color: #667eea;
  text-decoration: none;
  margin: 0 4px;
}

.footer-tips .link:hover {
  text-decoration: underline;
}
</style>
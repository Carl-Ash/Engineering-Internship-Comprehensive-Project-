<template>
  <div class="settings-page">
    <div class="page-heading">
      <h2>个人设置</h2>
    </div>

    <div class="settings-grid">
      <!-- 左侧：资料编辑 -->
      <div class="settings-main">
        <a-card :bordered="false" class="section-card">
          <template #title>
            <span class="section-title">编辑资料</span>
          </template>

          <a-form
            ref="formRef"
            :model="formData"
            :rules="rules"
            layout="vertical"
            @finish="handleSubmit"
          >
            <div class="form-avatar-row">
              <div class="avatar-upload-wrap" @click="triggerUpload">
                <a-avatar :size="80" :src="formData.userAvatar" class="form-avatar">
                  <template #icon><UserOutlined /></template>
                </a-avatar>
                <div class="avatar-overlay">
                  <CameraOutlined />
                  更换
                </div>
                <input
                  ref="fileInputRef"
                  type="file"
                  accept="image/*"
                  style="display: none"
                  @change="onFileChange"
                />
              </div>
              <div class="form-avatar-hint">
                <span class="hint-label">头像</span>
                <span class="hint-desc">点击头像上传本地图片</span>
              </div>
            </div>

            <a-form-item label="用户名" name="userName">
              <a-input v-model:value="formData.userName" placeholder="请输入用户名" size="large" />
            </a-form-item>

            <a-form-item label="头像 URL" name="userAvatar">
              <a-input v-model:value="formData.userAvatar" placeholder="支持粘贴网络图片链接" size="large" />
            </a-form-item>

            <a-form-item label="个人简介" name="userProfile">
              <a-textarea
                v-model:value="formData.userProfile"
                placeholder="介绍一下自己吧..."
                :rows="4"
                :maxlength="200"
                show-count
              />
            </a-form-item>

            <a-form-item>
              <a-button type="primary" html-type="submit" :loading="submitting" size="large" block>
                保存修改
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>

        <!-- 账号信息 -->
        <a-card :bordered="false" class="section-card" style="margin-top: 16px">
          <template #title>
            <span class="section-title">账号信息</span>
          </template>
          <a-descriptions :column="1" size="small">
            <a-descriptions-item label="账号">
              {{ loginUserStore.loginUser.userAccount || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="角色">
              <a-tag :color="loginUserStore.loginUser.userRole === 'superAdmin' ? 'gold' : loginUserStore.loginUser.userRole === 'admin' ? 'volcano' : 'blue'">
                {{ loginUserStore.loginUser.userRole === 'superAdmin' ? '超级管理员' : loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '普通用户' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="注册时间">
              {{ loginUserStore.loginUser.createTime ? dayjs(loginUserStore.loginUser.createTime).format('YYYY-MM-DD HH:mm') : '-' }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
      </div>

      <!-- 右侧：主题切换 -->
      <div class="settings-sidebar">
        <a-card :bordered="false" class="section-card">
          <template #title>
            <span class="section-title">主题切换</span>
          </template>
          <div class="theme-list">
            <div
              v-for="(t, idx) in themePresets"
              :key="idx"
              class="theme-card"
              :class="{ active: themeStore.currentIndex === idx }"
              @click="themeStore.setTheme(idx)"
            >
              <div class="theme-preview">
                <div class="preview-header" :style="{ background: t.bgHeader }"></div>
                <div class="preview-body" :style="{ background: t.bgPage }">
                  <div class="preview-card" :style="{ background: t.bgCard, borderColor: t.borderColor }"></div>
                  <div class="preview-text" :style="{ color: t.textSecondary }"></div>
                  <div class="preview-text short" :style="{ color: t.textSecondary }"></div>
                </div>
              </div>
              <div class="theme-meta">
                <span class="theme-name">{{ t.label }}</span>
                <CheckCircleFilled v-if="themeStore.currentIndex === idx" class="theme-check" :style="{ color: t.primary }" />
              </div>
            </div>
          </div>
        </a-card>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { UserOutlined, CheckCircleFilled, CameraOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { useLoginUserStore } from '@/stores/loginUser'
import { useThemeStore, themePresets } from '@/stores/theme'
import { updateUser } from '@/api/userController'

const loginUserStore = useLoginUserStore()
const themeStore = useThemeStore()

const formRef = ref<any>(null)
const submitting = ref(false)
const fileInputRef = ref<HTMLInputElement>()

const triggerUpload = () => {
  fileInputRef.value?.click()
}

const onFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = () => {
    formData.userAvatar = reader.result as string
  }
  reader.readAsDataURL(file)
  // 重置 input 以便重复选同一文件
  target.value = ''
}

const formData = reactive({
  userName: '',
  userAvatar: '',
  userProfile: '',
})

const rules = {
  userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
}

onMounted(() => {
  const user = loginUserStore.loginUser
  formData.userName = user.userName || ''
  formData.userAvatar = user.userAvatar || ''
  formData.userProfile = user.userProfile || ''
})

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const res = await updateUser({
      id: loginUserStore.loginUser.id,
      userName: formData.userName,
      userAvatar: formData.userAvatar || undefined,
      userProfile: formData.userProfile || undefined,
    })
    if (res.data.code === 0) {
      message.success('保存成功')
      await loginUserStore.fetchLoginUser()
    } else {
      message.error(res.data.message || '保存失败')
    }
  } catch {
    message.error('保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.settings-page {
  max-width: 960px;
  margin: 0 auto;
}

.page-heading h2 {
  margin: 0 0 20px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-color, #1a1a1a);
}

.settings-grid {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
  align-items: start;
}

@media (max-width: 768px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}

.section-card {
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.section-title {
  font-size: 16px;
  font-weight: 600;
}

/* 资料编辑 */
.form-avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color, #f0f0f0);
}

.avatar-upload-wrap {
  position: relative;
  cursor: pointer;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
}

.avatar-upload-wrap:hover .avatar-overlay {
  opacity: 1;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 11px;
  opacity: 0;
  transition: opacity 0.2s;
}

.form-avatar {
  flex-shrink: 0;
  border: 3px solid var(--border-color, #f0f0f0);
  box-sizing: content-box;
}

.form-avatar-hint {
  display: flex;
  flex-direction: column;
}

.hint-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color, #1a1a1a);
}

.hint-desc {
  font-size: 12px;
  color: var(--text-secondary, #8c8c8c);
  margin-top: 2px;
}

/* 主题列表 */
.theme-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.theme-card {
  border-radius: 10px;
  border: 2px solid transparent;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  padding: 2px;
}

.theme-card:hover {
  border-color: #d9d9d9;
}

.theme-card.active {
  border-color: var(--primary-color, #1890ff);
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.15);
}

.theme-preview {
  height: 80px;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.preview-header {
  height: 24px;
}

.preview-body {
  flex: 1;
  padding: 8px 10px;
  position: relative;
}

.preview-card {
  width: 40%;
  height: 16px;
  border-radius: 3px;
  border: 1px solid;
  margin-bottom: 6px;
}

.preview-text {
  height: 4px;
  width: 70%;
  background: currentColor;
  opacity: 0.3;
  border-radius: 2px;
  margin-bottom: 4px;
}

.preview-text.short {
  width: 45%;
}

.theme-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px 2px;
}

.theme-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-color, #1a1a1a);
}

.theme-check {
  font-size: 16px;
}
</style>

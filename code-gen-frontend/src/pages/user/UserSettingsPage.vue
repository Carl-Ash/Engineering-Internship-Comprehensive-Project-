<template>
  <div id="userSettingsPage">
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
              <div class="avatar-upload-wrap" @click="uploadingAvatar ? undefined : triggerUpload()">
                <a-avatar :size="80" :src="formData.userAvatar" class="form-avatar">
                  <template #icon><UserOutlined /></template>
                </a-avatar>
                <div class="avatar-overlay" v-if="!uploadingAvatar">
                  <CameraOutlined />
                  更换
                </div>
                <div class="avatar-overlay uploading" v-else>
                  <a-spin size="small" />
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
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { UserOutlined, CameraOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { useLoginUserStore } from '@/stores/loginUser'
import { updateUserProfile } from '@/api/userController'
import { uploadImage } from '@/api/appController'

const loginUserStore = useLoginUserStore()

const formRef = ref<any>(null)
const submitting = ref(false)
const uploadingAvatar = ref(false)
const fileInputRef = ref<HTMLInputElement>()

const triggerUpload = () => {
  fileInputRef.value?.click()
}

const onFileChange = async (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  if (file.size > 5 * 1024 * 1024) {
    message.error('图片大小不能超过 5MB')
    target.value = ''
    return
  }

  uploadingAvatar.value = true
  try {
    const res = await uploadImage(file)
    if (res.data.code === 0 && res.data.data) {
      formData.userAvatar = res.data.data
      message.success('头像上传成功')
    } else {
      message.error(res.data.message || '头像上传失败')
    }
  } catch {
    message.error('头像上传失败，请稍后重试')
  } finally {
    uploadingAvatar.value = false
    target.value = ''
  }
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
    const res = await updateUserProfile({
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
#userSettingsPage {
  padding: 28px;
  background: var(--bg-card);
  min-height: 100vh;
}

.page-heading h2 {
  margin: 0 0 20px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-color, #1a1a1a);
}

.settings-grid {
  max-width: 720px;
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

.avatar-overlay.uploading {
  opacity: 1;
  background: rgba(0, 0, 0, 0.35);
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
</style>

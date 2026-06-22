<template>
  <div id="appChatPage">
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
      </div>
      <div class="header-right">
        <a-tag v-if="genStatusTag" :color="genStatusTag.color">{{ genStatusTag.text }}</a-tag>
        <span v-if="appInfo?.version" class="version-text">v{{ appInfo.version }}</span>
        <a-button v-if="isOwner || isAdmin" type="default" size="small" @click="showAppDetail">应用详情</a-button>
        <a-button v-if="isOwner && isGenerating" type="default" danger size="small" @click="stopGeneration">停止生成</a-button>
        <a-button v-if="isOwner && appInfo?.deployKey" type="default" size="small" @click="undeployApp" :loading="undeploying">下线</a-button>
        <a-button v-if="isOwner" type="primary" size="small" @click="deployApp" :loading="deploying">部署</a-button>
      </div>
    </div>

    <div class="main-content">
      <div class="chat-section">
        <div class="messages-container" ref="messagesContainer">
          <div v-for="(message, index) in messages" :key="index" class="message-item">
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" />
              </div>
              <div class="message-content">
                <MarkdownRenderer v-if="message.content" :content="message.content" />
                <div v-if="message.loading" class="loading-indicator">
                  <a-spin size="small" />
                  <span>AI 正在思考...</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="input-container">
          <div class="input-wrapper">
            <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
              <span>
                <a-textarea
                  v-model:value="userInput"
                  placeholder="请描述你想生成的网站，越详细效果越好哦"
                  :rows="4"
                  :maxlength="1000"
                  @keydown.enter.prevent="sendMessage"
                  disabled
                />
              </span>
            </a-tooltip>
            <a-textarea
              v-else
              v-model:value="userInput"
              placeholder="请描述你想生成的网站，越详细效果越好哦"
              :rows="4"
              :maxlength="1000"
              @keydown.enter.prevent="sendMessage"
              :disabled="isGenerating"
            />
            <div class="input-actions">
              <a-button
                type="primary"
                @click="sendMessage"
                :loading="isGenerating"
                :disabled="!isOwner"
              >
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>

      <div class="preview-section">
        <div class="preview-header">
          <h3>生成后的网页展示</h3>
          <div class="preview-actions">
            <a-button v-if="previewUrl" type="link" @click="openInNewTab">
              <template #icon>
                <ExportOutlined />
              </template>
              新窗口打开
            </a-button>
          </div>
        </div>
        <div class="preview-content">
          <div v-if="!previewUrl && !isGenerating" class="preview-placeholder">
            <div class="placeholder-icon">🌐</div>
            <p>网站文件生成完成后将在这里展示</p>
          </div>
          <div v-else-if="isGenerating" class="preview-loading">
            <a-spin size="large" />
            <p>正在生成网站...</p>
          </div>
          <iframe
            v-else
            :src="previewUrl"
            class="preview-iframe"
            frameborder="0"
            @load="onIframeLoad"
          ></iframe>
        </div>
      </div>
    </div>

    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="appInfo"
      :show-actions="isOwner || isAdmin"
      @edit="editApp"
      @delete="deleteApp"
    />

    <DeploySuccessModal
      v-model:open="deployModalVisible"
      :deploy-url="deployUrl"
      @open-site="openDeployedSite"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  getAppVoById,
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
  cancelGenCode,
  undeployApp as undeployAppApi,
} from '@/api/appController'
import {
  CloseCircleFilled,
} from '@ant-design/icons-vue'
import { CodeGenTypeEnum } from '@/utils/codeGenTypes'
import request from '@/request'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import aiAvatar from '@/assets/aiAvatar.png'
import { API_BASE_URL, getStaticPreviewUrl } from '@/config/env'

import {
  SendOutlined,
  ExportOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appInfo = ref<API.AppVO>()
const appId = ref<string>()

interface Message {
  type: 'user' | 'ai'
  content: string
  loading?: boolean
}

const messages = ref<Message[]>([])
const userInput = ref('')
const isGenerating = ref(false)
const messagesContainer = ref<HTMLElement>()
const hasInitialConversation = ref(false)
const activeEventSource = ref<EventSource | null>(null)
const activeTimeout = ref<ReturnType<typeof setTimeout> | null>(null)

const previewUrl = ref('')
const previewReady = ref(false)

const deploying = ref(false)
const undeploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

const genStatusTag = computed(() => {
  switch (appInfo.value?.genStatus) {
    case 'generating': return { color: 'processing', text: '生成中' }
    case 'completed': return { color: 'success', text: '已完成' }
    case 'failed': return { color: 'error', text: '生成失败' }
    default: return null
  }
})

const isOwner = computed(() => {
  return appInfo.value?.userId === loginUserStore.loginUser.id
})

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin' || loginUserStore.loginUser.userRole === 'superAdmin'
})

const appDetailVisible = ref(false)

const showAppDetail = () => {
  appDetailVisible.value = true
}

const fetchAppInfo = async () => {
  const id = route.params.id as string
  if (!id) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }

  appId.value = id

  try {
    const res = await getAppVoById({ id })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data

      const isViewMode = route.query.view === '1'

      if (appInfo.value.initPrompt && !isViewMode && !hasInitialConversation.value) {
        hasInitialConversation.value = true
        await sendInitialMessage(appInfo.value.initPrompt)
      }
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败：', error)
    message.error('获取应用信息失败')
    router.push('/')
  }
}

const sendInitialMessage = async (prompt: string) => {
  messages.value.push({
    type: 'user',
    content: prompt,
  })

  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom(true)

  isGenerating.value = true
  await generateCode(prompt, aiMessageIndex)
}

const sendMessage = async () => {
  if (!userInput.value.trim() || isGenerating.value) {
    return
  }

  const msg = userInput.value.trim()
  userInput.value = ''

  messages.value.push({
    type: 'user',
    content: msg,
  })

  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom(true)

  isGenerating.value = true
  await generateCode(msg, aiMessageIndex)
}

const generateCode = async (userMessage: string, aiMessageIndex: number) => {
  let eventSource: EventSource | null = null
  let streamCompleted = false
  let retryCount = 0
  let timeoutTimer: ReturnType<typeof setTimeout> | null = null

  const clearStream = () => {
    if (timeoutTimer) { clearTimeout(timeoutTimer); timeoutTimer = null; activeTimeout.value = null }
    isGenerating.value = false
    streamCompleted = true
    eventSource?.close()
    activeEventSource.value = null
  }

  try {
    const baseURL = request.defaults.baseURL || API_BASE_URL

    const params = new URLSearchParams({
      appId: appId.value || '',
      message: userMessage,
    })

    const url = `${baseURL}/app/chat/gen/code?${params}`
    console.log('[SSE] 连接地址:', url)

    eventSource = new EventSource(url, {
      withCredentials: true,
    })
    activeEventSource.value = eventSource

    let fullContent = ''

    // 60秒无数据超时
    const resetTimeout = () => {
      if (timeoutTimer) clearTimeout(timeoutTimer)
      timeoutTimer = setTimeout(() => {
        if (!streamCompleted) {
          console.error('[SSE] 数据接收超时')
          clearStream()
          handleError(new Error('SSE 响应超时，请重试'), aiMessageIndex)
        }
      }, 60000)
    }
    resetTimeout()

    eventSource.onopen = function () {
      console.log('[SSE] 连接已建立')
      retryCount = 0
    }

    eventSource.onmessage = function (event) {
      if (streamCompleted) return

      try {
        const parsed = JSON.parse(event.data)
        const content = parsed.v

        if (content !== undefined && content !== null) {
          fullContent += content
          const msgItem = messages.value[aiMessageIndex]
          if (msgItem) {
            msgItem.content = fullContent
            msgItem.loading = false
          }
          resetTimeout()
          scrollToBottom()
        }
      } catch {
        console.warn('[SSE] 收到非 JSON 数据:', event.data)
      }
    }

    eventSource.addEventListener('complete', function () {
      if (streamCompleted) return
      console.log('[SSE] 收到 complete 事件，流结束')
      clearStream()

      setTimeout(async () => {
        await fetchAppInfo()
        updatePreview()
      }, 1000)
    })

    eventSource.onerror = function () {
      if (streamCompleted || !isGenerating.value) return

      retryCount++
      console.warn(`[SSE] 连接错误 (第${retryCount}次), readyState=${eventSource?.readyState}`)

      // EventSource 会自动重连（readyState=0 CONNECTING），只在彻底关闭时显示错误
      if (eventSource?.readyState === EventSource.CLOSED) {
        console.error('[SSE] 连接已关闭，停止重试')
        clearStream()
        handleError(new Error('SSE 连接失败，请检查后端服务'), aiMessageIndex)
      }
      // readyState === CONNECTING 时浏览器会自动重连，不干预
    }
  } catch (error) {
    console.error('[SSE] 创建连接失败：', error)
    handleError(error, aiMessageIndex)
  }
}

const handleError = (error: unknown, aiMessageIndex: number) => {
  console.error('生成代码失败：', error)
  const msgItem = messages.value[aiMessageIndex]
  if (msgItem) {
    msgItem.content = '抱歉，生成过程中出现了错误，请重试。'
    msgItem.loading = false
  }
  message.error('生成失败，请重试')
  isGenerating.value = false
}

const updatePreview = () => {
  if (appId.value) {
    const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
    const newPreviewUrl = getStaticPreviewUrl(codeGenType, appId.value)
    previewUrl.value = newPreviewUrl
    previewReady.value = true
  }
}

const scrollToBottom = (force = false) => {
  const el = messagesContainer.value
  if (!el) return
  // 只在用户接近底部时才自动滚动（距离底部 150px 以内），避免滚动劫持
  const isNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 150
  if (force || isNearBottom) {
    el.scrollTop = el.scrollHeight
  }
}

const stopGeneration = async () => {
  if (!appId.value) return
  try {
    await cancelGenCode({ appId: appId.value })
    message.info('已发送停止信号')
  } catch {
    message.error('停止失败')
  }
}

const undeployApp = async () => {
  if (!appId.value) return
  undeploying.value = true
  try {
    const res = await undeployAppApi({ appId: appId.value })
    if (res.data.code === 0) {
      message.success('已下线')
      await fetchAppInfo()
    }
  } catch {
    message.error('下线失败')
  } finally {
    undeploying.value = false
  }
}

const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }

  deploying.value = true
  try {
    const res = await deployAppApi({
      appId: appId.value,
    })

    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalVisible.value = true
      message.success('部署成功')
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

const onIframeLoad = () => {
  previewReady.value = true
}

const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

const deleteApp = async () => {
  if (!appInfo.value?.id) return

  try {
    const res = await deleteAppApi({ id: appInfo.value.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      appDetailVisible.value = false
      router.push('/')
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

onMounted(() => {
  fetchAppInfo()
})

onUnmounted(() => {
  if (activeTimeout.value) { clearTimeout(activeTimeout.value); activeTimeout.value = null }
  activeEventSource.value?.close()
  activeEventSource.value = null
  isGenerating.value = false
})
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 10px;
  background:
    linear-gradient(180deg, var(--bg-page) 0%, var(--bg-card) 100%),
    radial-gradient(circle at 20% 80%, var(--primary-color) 0%, transparent 15%),
    radial-gradient(circle at 80% 20%, var(--hero-gradient-mid) 0%, transparent 12%);
}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: var(--bg-header);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid var(--border-color);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color);
}

.header-right {
  display: flex;
  gap: 8px;
}

.main-content {
  flex: 1;
  display: flex;
  gap: 10px;
  padding-top: 10px;
  overflow: hidden;
}

.chat-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.messages-container {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.message-item {
  margin-bottom: 14px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 10px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 10px;
}

.message-content {
  max-width: 75%;
  padding: 12px 16px;
  border-radius: 16px;
  line-height: 1.6;
  word-wrap: break-word;
  position: relative;
}

.user-message .message-content {
  background: linear-gradient(135deg, var(--primary-color) 0%, var(--hero-gradient-end) 100%);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.ai-message .message-content {
  background: var(--bg-page);
  color: var(--text-color);
  border-bottom-left-radius: 4px;
  border: 1px solid var(--border-color);
}

.message-avatar {
  flex-shrink: 0;
}

.message-avatar :deep(.ant-avatar) {
  border: 2px solid var(--border-color);
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
  padding: 8px 0;
}

.input-container {
  padding: 12px 16px;
  background: var(--bg-header);
  border-top: 1px solid var(--border-color);
}

.input-wrapper {
  position: relative;
}

.input-wrapper :deep(.ant-input) {
  padding-right: 56px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-page);
  color: var(--text-color);
  transition: all 0.3s;
}

.input-wrapper :deep(.ant-input)::placeholder {
  color: var(--text-secondary);
}

.input-wrapper :deep(.ant-input):focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(var(--primary-color-rgb), 0.1);
  background: var(--bg-card);
}

.input-actions {
  position: absolute;
  bottom: 6px;
  right: 6px;
}

.input-actions .ant-btn-primary {
  border-radius: 50%;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  transition: all 0.3s;
}

.input-actions .ant-btn-primary:hover {
  transform: scale(1.08);
}

.preview-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-header);
}

.preview-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-color);
}

.preview-actions {
  display: flex;
  gap: 6px;
}

.preview-content {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: var(--bg-page);
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-secondary);
}

.placeholder-icon {
  font-size: 56px;
  margin-bottom: 16px;
  animation: floatIcon 3s ease-in-out infinite;
}

@keyframes floatIcon {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-secondary);
}

.preview-loading p {
  margin-top: 16px;
  font-size: 14px;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }
  .chat-section,
  .preview-section {
    flex: none;
    height: 50vh;
  }
}

@media (max-width: 768px) {
  #appChatPage {
    padding: 6px;
  }
  .header-bar {
    padding: 8px 12px;
  }
  .app-name {
    font-size: 15px;
  }
  .main-content {
    padding-top: 6px;
    gap: 6px;
  }
  .message-content {
    max-width: 85%;
    padding: 10px 14px;
  }
  .messages-container {
    padding: 12px;
  }
  .input-container {
    padding: 10px 12px;
  }
}
</style>
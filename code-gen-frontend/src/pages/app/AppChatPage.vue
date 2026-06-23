<template>
  <div id="appChatPage">
    <!-- 顶部导航栏 -->
    <div class="header-bar">
      <div class="header-left">
        <a-button type="text" class="back-btn" @click="router.push('/')">
          <template #icon><ArrowLeftOutlined /></template>
        </a-button>
        <div class="app-info">
          <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
          <div class="app-meta">
            <a-tag v-if="genStatusTag" :color="genStatusTag.color" size="small">{{ genStatusTag.text }}</a-tag>
            <span v-if="appInfo?.version" class="version-text">v{{ appInfo.version }}</span>
            <span class="codegen-type-badge">{{ codeGenTypeLabel }}</span>
          </div>
        </div>
      </div>
      <div class="header-right">
        <a-space :size="8" wrap>
          <a-button v-if="isOwner || isAdmin" size="small" @click="exportChatHistory">导出对话</a-button>
          <a-button v-if="isOwner || isAdmin" size="small" @click="showAppDetail">应用详情</a-button>
          <a-button v-if="isOwner && isGenerating" size="small" danger @click="stopGeneration">停止生成</a-button>
          <a-button v-if="isOwner && appInfo?.deployKey" size="small" @click="undeployApp" :loading="undeploying">下线</a-button>
          <a-button v-if="isOwner" type="primary" size="small" @click="deployApp" :loading="deploying">部署</a-button>
        </a-space>
      </div>
    </div>

    <!-- 主体内容 -->
    <div class="main-content">
      <!-- 左侧：对话区 -->
      <div class="chat-section">
        <div class="messages-container" ref="messagesContainer">
          <div v-if="hasMoreHistory" class="load-more-container">
            <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory" size="small">
              加载更多历史消息
            </a-button>
          </div>

          <div v-if="messages.length === 0 && !isGenerating" class="empty-chat">
            <div class="empty-icon">💬</div>
            <h3>开始对话</h3>
            <p>在下方输入你想要创建的网站描述，AI 将为你生成完整代码</p>
          </div>

          <div v-for="(message, index) in messages" :key="index" class="message-item">
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" :size="36" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" :size="36" />
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

        <!-- 输入区 -->
        <div class="input-container">
          <div class="input-wrapper">
            <a-textarea
              v-model:value="userInput"
              :placeholder="isOwner ? '继续描述你想要的修改...' : '无法在别人的作品下对话'"
              :rows="3"
              :maxlength="2000"
              @keydown.enter.exact.prevent="sendMessage"
              :disabled="!isOwner || isGenerating"
              class="chat-input"
            />
            <div class="input-footer">
              <span class="char-count">{{ userInput.length }}/2000</span>
              <a-button
                type="primary"
                @click="sendMessage"
                :loading="isGenerating"
                :disabled="!isOwner || !userInput.trim()"
                class="send-btn"
              >
                <template #icon><SendOutlined /></template>
                发送
              </a-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：预览区 -->
      <div class="preview-section">
        <div class="preview-header">
          <div class="preview-header-left">
            <h3>实时预览</h3>
            <a-tag v-if="previewReady && previewUrl" color="success" size="small">已就绪</a-tag>
          </div>
          <div class="preview-actions">
            <a-button v-if="previewUrl" size="small" @click="refreshPreview">
              <template #icon><ReloadOutlined /></template>
            </a-button>
            <a-button v-if="previewUrl" size="small" @click="openInNewTab">
              <template #icon><ExportOutlined /></template>
            </a-button>
          </div>
        </div>
        <div class="preview-content">
          <div v-if="!previewUrl && !isGenerating" class="preview-placeholder">
            <div class="placeholder-icon">🌐</div>
            <h3>预览区</h3>
            <p>网站文件生成完成后将在这里展示</p>
            <div class="placeholder-hints">
              <span><CheckCircleOutlined /> 输入需求描述网站</span>
              <span><CheckCircleOutlined /> AI 自动生成完整代码</span>
              <span><CheckCircleOutlined /> 实时预览效果</span>
            </div>
          </div>
          <div v-else-if="isGenerating" class="preview-loading">
            <a-spin size="large" />
            <p>正在生成网站...</p>
            <span class="loading-sub">请耐心等待，复杂需求可能需要一些时间</span>
          </div>
          <iframe
            v-else
            :key="iframeKey"
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
import { listAppChatHistory, exportChatHistoryUrl } from '@/api/chatHistoryController'
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
  ArrowLeftOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
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
  createTime?: string
}

const messages = ref<Message[]>([])
const userInput = ref('')
const isGenerating = ref(false)
const messagesContainer = ref<HTMLElement>()
const activeEventSource = ref<EventSource | null>(null)
const activeTimeout = ref<ReturnType<typeof setTimeout> | null>(null)

// 对话历史游标分页
const loadingHistory = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
const historyLoaded = ref(false)

const previewUrl = ref('')
const previewReady = ref(false)
const iframeKey = ref(0)

const codeGenTypeLabel = computed(() => {
  const type = appInfo.value?.codeGenType
  if (!type) return ''
  return type === 'html' ? 'HTML 模式' : '多文件模式'
})

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

// 加载对话历史（游标分页）
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value || loadingHistory.value) return
  loadingHistory.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value as unknown as number,
      pageSize: 10,
    }
    if (isLoadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value
    }
    const res = await listAppChatHistory(params)
    if (res.data.code === 0 && res.data.data) {
      const chatHistories = res.data.data.records || []
      if (chatHistories.length > 0) {
        // 后端返回时间降序，反转为升序（老消息在前）
        const historyMessages: Message[] = chatHistories
          .map((chat) => ({
            type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
            content: chat.message || '',
            createTime: chat.createTime,
          }))
          .reverse()
        if (isLoadMore) {
          messages.value.unshift(...historyMessages)
        } else {
          messages.value = historyMessages
        }
        // 游标更新为当前批次最老消息的时间
        lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
        hasMoreHistory.value = chatHistories.length === 10
      } else {
        hasMoreHistory.value = false
      }
      historyLoaded.value = true
    }
  } catch (error) {
    console.error('加载对话历史失败：', error)
    message.error('加载对话历史失败')
  } finally {
    loadingHistory.value = false
  }
}

const loadMoreHistory = async () => {
  await loadChatHistory(true)
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
    const res = await getAppVoById({ id: Number(id) })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data

      // 先加载对话历史
      await loadChatHistory()

      // 有至少2条对话记录时展示网站预览
      if (messages.value.length >= 2) {
        updatePreview()
      }

      // 只有自己的应用且没有对话历史时，自动发送初始提示词
      if (
        appInfo.value.initPrompt &&
        isOwner.value &&
        messages.value.length === 0 &&
        historyLoaded.value
      ) {
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
    await cancelGenCode({ appId: Number(appId.value) })
    message.info('已发送停止信号')
  } catch {
    message.error('停止失败')
  }
}

const undeployApp = async () => {
  if (!appId.value) return
  undeploying.value = true
  try {
    const res = await undeployAppApi({ appId: Number(appId.value) })
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
      appId: Number(appId.value),
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

const refreshPreview = () => {
  if (previewUrl.value) {
    previewReady.value = false
    iframeKey.value++
  }
}

const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

const exportChatHistory = async () => {
  if (!appId.value) return
  try {
    const url = exportChatHistoryUrl(appId.value)
    const response = await fetch(url, { credentials: 'include' })
    if (!response.ok) {
      const errorText = await response.text()
      if (response.status === 401 || errorText.includes('未登录')) {
        message.error('请先登录后再导出')
      } else {
        message.error('导出失败，请重试')
      }
      return
    }
    const blob = await response.blob()
    const contentDisposition = response.headers.get('Content-Disposition')
    let fileName = '对话历史.md'
    if (contentDisposition) {
      const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?([^;]+)/i)
      if (match && match[1]) {
        fileName = decodeURIComponent(match[1].trim())
      }
    }
    const downloadUrl = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = downloadUrl
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(downloadUrl)
    message.success('导出成功')
  } catch {
    message.error('导出失败，请检查网络')
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
  height: calc(100vh - 64px - 62px);
  display: flex;
  flex-direction: column;
  padding: 12px;
  background: var(--bg-page);
  overflow: hidden;
}

/* ====== 顶部导航栏 ====== */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: var(--bg-card);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  flex-shrink: 0;
  min-height: 48px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.back-btn {
  color: var(--text-secondary);
  font-size: 18px;
  flex-shrink: 0;
}

.back-btn:hover {
  color: var(--primary-color);
}

.app-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-color);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.version-text {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
}

.codegen-type-badge {
  font-size: 11px;
  color: var(--text-secondary);
  padding: 0 6px;
  background: var(--bg-page);
  border-radius: 4px;
  border: 1px solid var(--border-color);
}

.header-right {
  flex-shrink: 0;
}

/* ====== 主体 ====== */
.main-content {
  flex: 1;
  display: flex;
  gap: 10px;
  overflow: hidden;
  min-height: 0;
}

/* ====== 左侧对话区 ====== */
.chat-section {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.messages-container {
  flex: 1;
  padding: 14px;
  overflow-y: auto;
  scroll-behavior: smooth;
  display: flex;
  flex-direction: column;
}

.messages-container::-webkit-scrollbar {
  width: 5px;
}

.messages-container::-webkit-scrollbar-track {
  background: transparent;
}

.messages-container::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 10px;
}

.load-more-container {
  text-align: center;
  padding: 8px 0 20px;
}

/* 空状态 */
.empty-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  text-align: center;
  padding: 40px 20px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-chat h3 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color);
}

.empty-chat p {
  margin: 0;
  font-size: 14px;
  max-width: 300px;
  line-height: 1.6;
}

/* 消息 */
.message-item {
  margin-bottom: 14px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 8px;
}

.message-content {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 16px;
  line-height: 1.6;
  word-wrap: break-word;
  overflow-wrap: break-word;
  font-size: 13px;
}

.user-message .message-content {
  background: linear-gradient(135deg, #3b82f6 0%, #6366f1 100%);
  color: #fff;
  border-bottom-right-radius: 6px;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.2);
}

.ai-message .message-content {
  background: var(--bg-page);
  color: var(--text-color);
  border-bottom-left-radius: 6px;
  border: 1px solid var(--border-color);
}

.ai-message .message-content :deep(pre) {
  background: var(--bg-card);
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
}

.ai-message .message-content :deep(code) {
  font-size: 13px;
}

.message-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.message-avatar :deep(.ant-avatar) {
  border: 2px solid var(--border-color);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
  padding: 6px 0;
  font-size: 14px;
}

/* 输入区 */
.input-container {
  padding: 10px 14px;
  background: var(--bg-header);
  border-top: 1px solid var(--border-color);
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  flex-direction: column;
}

.chat-input:deep(.ant-input) {
  border-radius: 10px;
  border: 1px solid var(--border-color);
  background: var(--bg-page);
  color: var(--text-color);
  font-size: 13px;
  line-height: 1.6;
  padding: 8px 12px;
  resize: none;
  transition: all 0.25s;
  min-height: 44px;
  max-height: 120px;
}

.chat-input:deep(.ant-input)::placeholder {
  color: var(--text-secondary);
}

.chat-input:deep(.ant-input):focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.08);
  background: var(--bg-card);
}

.input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.char-count {
  font-size: 11px;
  color: var(--text-secondary);
}

.send-btn {
  border-radius: 8px;
  font-weight: 500;
  padding: 4px 16px;
  height: 32px;
  font-size: 13px;
  transition: all 0.25s;
}

.send-btn:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

/* ====== 右侧预览区 ====== */
.preview-section {
  flex: 1.5;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-header);
  flex-shrink: 0;
  min-height: 40px;
}

.preview-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
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
  background: #fff;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-secondary);
  padding: 40px 20px;
}

.placeholder-icon {
  font-size: 60px;
  margin-bottom: 20px;
  animation: floatIcon 3s ease-in-out infinite;
}

@keyframes floatIcon {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-12px); }
}

.preview-placeholder h3 {
  margin: 0 0 8px;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-color);
}

.preview-placeholder p {
  margin: 0 0 24px;
  font-size: 14px;
}

.placeholder-hints {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.placeholder-hints span {
  display: flex;
  align-items: center;
  gap: 8px;
}

.placeholder-hints :deep(.anticon) {
  color: var(--primary-color);
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
  margin: 18px 0 4px;
  font-size: 15px;
  font-weight: 500;
}

.loading-sub {
  font-size: 13px;
  color: var(--text-secondary);
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

/* ====== 响应式 ====== */
@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }
  .chat-section,
  .preview-section {
    flex: none;
    min-height: 280px;
  }
  .chat-section {
    height: 40vh;
  }
  .preview-section {
    height: 52vh;
  }
}

@media (max-width: 768px) {
  #appChatPage {
    padding: 6px;
    height: calc(100vh - 64px - 50px);
  }
  .header-bar {
    padding: 6px 10px;
    border-radius: 10px;
  }
  .app-name {
    font-size: 14px;
  }
  .main-content {
    gap: 6px;
  }
  .message-content {
    max-width: 85%;
    padding: 8px 12px;
    font-size: 12px;
  }
  .messages-container {
    padding: 10px;
  }
  .input-container {
    padding: 8px 10px;
  }
  .preview-header {
    padding: 8px 12px;
  }
}
</style>
<template>
  <PageContainer>
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-header-left">
        <h2 class="page-title">对话管理</h2>
        <p class="page-desc">管理平台中的所有对话消息，查看和删除对话记录</p>
      </div>
    </div>

    <!-- 搜索表单 -->
    <div class="search-bar">
      <a-form layout="inline" :model="searchParams" @finish="doSearch">
        <a-form-item label="消息内容">
          <a-input v-model:value="searchParams.message" placeholder="输入消息内容" allow-clear />
        </a-form-item>
        <a-form-item label="消息类型">
          <a-select
            v-model:value="searchParams.messageType"
            placeholder="选择消息类型"
            allow-clear
            style="width: 140px"
          >
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="user">用户消息</a-select-option>
            <a-select-option value="assistant">AI消息</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="应用ID">
          <a-input v-model:value="searchParams.appId" placeholder="输入应用ID" allow-clear />
        </a-form-item>
        <a-form-item label="用户ID">
          <a-input v-model:value="searchParams.userId" placeholder="输入用户ID" allow-clear />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit">搜索</a-button>
        </a-form-item>
      </a-form>
    </div>

    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      :loading="tableLoading"
      @change="doTableChange"
      :scroll="{ x: 1200 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'message'">
          <a-tooltip :title="record.message">
            <div class="message-text">{{ record.message }}</div>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'messageType'">
          <a-tag :color="record.messageType === 'user' ? 'blue' : 'green'">
            {{ record.messageType === 'user' ? '用户消息' : 'AI消息' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="primary" size="small" @click="viewAppChat(record.appId)">
              查看对话
            </a-button>
            <a-popconfirm title="确定要删除这条消息吗？" @confirm="doDelete(record.id)">
              <a-button danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </PageContainer>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  listAllChatHistoryByPageForAdmin,
  deleteChatHistoryByAdmin,
} from '@/api/chatHistoryController'
import { formatTime } from '@/utils/time'
import PageContainer from '@/components/PageContainer.vue'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80, fixed: 'left' },
  { title: '消息内容', dataIndex: 'message', width: 300 },
  { title: '消息类型', dataIndex: 'messageType', width: 100 },
  { title: '应用ID', dataIndex: 'appId', width: 80 },
  { title: '用户ID', dataIndex: 'userId', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', width: 160 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' },
]

const data = ref<API.ChatHistory[]>([])
const total = ref(0)
const tableLoading = ref(false)

const searchParams = reactive<API.ChatHistoryQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

const fetchData = async () => {
  tableLoading.value = true
  try {
    const res = await listAllChatHistoryByPageForAdmin({
      ...searchParams,
    })
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = Number(res.data.data.totalRow) || 0
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (error) {
    console.error('获取数据失败：', error)
    message.error('获取数据失败')
  } finally {
    tableLoading.value = false
  }
}

onMounted(() => {
  fetchData()
})

const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
}))

const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

const viewAppChat = (appId: number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}`)
  }
}

const doDelete = async (id: number | undefined) => {
  if (!id) return
  try {
    const res = await deleteChatHistoryByAdmin({ id })
    if (res.data.code === 0) {
      message.success('删除成功')
      fetchData()
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-color);
}

.page-desc {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.search-bar {
  padding: 16px 20px;
  background: var(--bg-page);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  margin-bottom: 24px;
}

:deep(.ant-form-inline .ant-form-item) {
  margin-bottom: 8px;
}

:deep(.ant-input),
:deep(.ant-select-selector) {
  border-radius: 8px;
  border-color: var(--border-color);
  transition: all 0.2s;
}

:deep(.ant-input):focus,
:deep(.ant-select-focused .ant-select-selector) {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(var(--primary-color-rgb), 0.08);
}

:deep(.ant-btn-primary) {
  border-radius: 8px;
  font-weight: 500;
}

:deep(.ant-table) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.ant-table-thead > tr > th) {
  background: var(--bg-page);
  color: var(--text-secondary);
  font-weight: 600;
  font-size: 13px;
  border-bottom: 2px solid var(--border-color);
  padding: 14px 16px;
}

:deep(.ant-table-tbody > tr > td) {
  vertical-align: middle;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color);
}

:deep(.ant-table-tbody > tr:hover > td) {
  background: var(--bg-page);
}

:deep(.ant-table-tbody > tr:last-child > td) {
  border-bottom: none;
}

:deep(.ant-tag) {
  border-radius: 6px;
  font-weight: 500;
}

.message-text {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.ant-btn-sm) {
  border-radius: 6px;
}

:deep(.ant-pagination) {
  margin-top: 20px;
}

:deep(.ant-pagination .ant-pagination-item-active) {
  border-color: var(--primary-color);
  background: var(--primary-color);
}

:deep(.ant-pagination .ant-pagination-item-active a) {
  color: #fff;
}
</style>

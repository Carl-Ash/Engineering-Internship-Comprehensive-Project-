<template>
  <PageContainer>
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-header-left">
        <h2 class="page-title">应用管理</h2>
        <p class="page-desc">管理平台中的所有应用，设置精选和优先级</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon stat-icon-total">
          <AppstoreOutlined />
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ total }}</span>
          <span class="stat-label">总应用</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-featured">
          <StarOutlined />
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ featuredCount }}</span>
          <span class="stat-label">精选应用</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-deployed">
          <CloudOutlined />
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ deployedCount }}</span>
          <span class="stat-label">已部署</span>
        </div>
      </div>
    </div>

    <!-- 搜索表单 -->
    <div class="search-bar">
      <a-form layout="inline" :model="searchParams" @finish="doSearch">
        <a-form-item label="应用名称">
          <a-input v-model:value="searchParams.appName" placeholder="输入应用名称" allow-clear />
        </a-form-item>
        <a-form-item v-if="isAdmin" label="创建者">
          <a-input v-model:value="searchParams.userId" placeholder="输入用户ID" allow-clear />
        </a-form-item>
        <a-form-item v-if="isAdmin" label="生成类型">
          <a-select
            v-model:value="searchParams.codeGenType"
            placeholder="选择生成类型"
            allow-clear
            style="width: 150px"
          >
            <a-select-option value="">全部</a-select-option>
            <a-select-option
              v-for="option in CODE_GEN_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="isAdmin" label="状态">
          <a-select
            v-model:value="searchParams.genStatus"
            placeholder="选择状态"
            allow-clear
            style="width: 120px"
          >
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="none">未生成</a-select-option>
            <a-select-option value="generating">生成中</a-select-option>
            <a-select-option value="completed">已完成</a-select-option>
            <a-select-option value="failed">失败</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="isAdmin" label="可见">
          <a-select
            v-model:value="searchParams.visibility"
            placeholder="可见范围"
            allow-clear
            style="width: 110px"
          >
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="public">公开</a-select-option>
            <a-select-option value="private">私有</a-select-option>
          </a-select>
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
      @change="doTableChange"
      :scroll="{ x: 1400 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'cover'">
          <a-image v-if="record.cover" :src="record.cover" :width="80" :height="60" />
          <div v-else class="no-cover">无封面</div>
        </template>
        <template v-else-if="column.dataIndex === 'initPrompt'">
          <a-tooltip :title="record.initPrompt">
            <div class="prompt-text">{{ record.initPrompt }}</div>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'codeGenType'">
          {{ formatCodeGenType(record.codeGenType) }}
        </template>
        <template v-else-if="column.dataIndex === 'genStatus'">
          <a-tag v-if="record.genStatus === 'none'" color="default">未生成</a-tag>
          <a-tag v-else-if="record.genStatus === 'generating'" color="processing">生成中</a-tag>
          <a-tag v-else-if="record.genStatus === 'completed'" color="success">已完成</a-tag>
          <a-tag v-else-if="record.genStatus === 'failed'" color="error">失败</a-tag>
          <span v-else>{{ record.genStatus }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'visibility'">
          <a-tag v-if="record.visibility === 'public'" color="blue">公开</a-tag>
          <a-tag v-else-if="record.visibility === 'private'" color="orange">私有</a-tag>
          <span v-else>{{ record.visibility }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'priority'">
          <a-tag v-if="record.priority === 99" color="gold">精选</a-tag>
          <span v-else>{{ record.priority || 0 }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'deployedTime'">
          <span v-if="record.deployedTime">{{ formatTime(record.deployedTime) }}</span>
          <span v-else class="text-gray">未部署</span>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-else-if="column.dataIndex === 'user'">
          <UserInfo :user="record.user" size="small" />
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="primary" size="small" @click="editApp(record)">编辑</a-button>
            <a-button
              v-if="isAdmin"
              size="small"
              @click="toggleFeatured(record)"
              :class="{ 'featured-btn': record.priority === 99 }"
            >
              {{ record.priority === 99 ? '取消精选' : '精选' }}
            </a-button>
            <a-popconfirm title="确定要删除这个应用吗？" @confirm="doDelete(record.id)">
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
import { AppstoreOutlined, StarOutlined, CloudOutlined } from '@ant-design/icons-vue'
import {
  listAppVoByPageByAdmin,
  listMyAppVoByPage,
  deleteAppByAdmin,
  deleteApp,
  updateAppByAdmin,
} from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { CODE_GEN_TYPE_OPTIONS, formatCodeGenType } from '@/utils/codeGenTypes'
import { formatTime } from '@/utils/time'
import PageContainer from '@/components/PageContainer.vue'
import UserInfo from '@/components/UserInfo.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin' || loginUserStore.loginUser.userRole === 'superAdmin')

const featuredCount = computed(() => data.value.filter((a) => a.priority === 99).length)
const deployedCount = computed(() => data.value.filter((a) => a.deployKey).length)

const data = ref<API.AppVO[]>([])
const total = ref(0)

const searchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

const fetchData = async () => {
  try {
    const params = { ...searchParams }
    // 普通用户去掉 userId、codeGenType、genStatus 搜索条件
    if (!isAdmin.value) {
      delete params.userId
      delete params.codeGenType
      delete params.genStatus
    }

    const api = isAdmin.value ? listAppVoByPageByAdmin : listMyAppVoByPage
    const res = await api(params)
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (error) {
    console.error('获取数据失败：', error)
    message.error('获取数据失败')
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

const editApp = (app: API.AppVO) => {
  router.push(`/app/edit/${app.id}`)
}

const toggleFeatured = async (app: API.AppVO) => {
  if (!app.id) return
  const newPriority = app.priority === 99 ? 0 : 99
  try {
    const res = await updateAppByAdmin({ id: app.id, priority: newPriority })
    if (res.data.code === 0) {
      message.success(newPriority === 99 ? '已设为精选' : '已取消精选')
      fetchData()
    } else {
      message.error('操作失败：' + res.data.message)
    }
  } catch (error) {
    console.error('操作失败：', error)
    message.error('操作失败')
  }
}

const doDelete = async (id: number | string | undefined) => {
  if (!id) return
  try {
    const api = isAdmin.value ? deleteAppByAdmin : deleteApp
    const res = await api({ id: Number(id) })
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

// 列定义：管理员看到创建者列，普通用户不看到
const adminExtraCol = { title: '创建者', dataIndex: 'user', width: 120 }

const columns = computed(() => {
  const cols: Record<string, unknown>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, fixed: 'left' },
    { title: '应用名称', dataIndex: 'appName', width: 150 },
    { title: '封面', dataIndex: 'cover', width: 100 },
    { title: '初始提示词', dataIndex: 'initPrompt', width: 200 },
    { title: '生成类型', dataIndex: 'codeGenType', width: 100 },
    { title: '状态', dataIndex: 'genStatus', width: 90 },
    { title: '可见', dataIndex: 'visibility', width: 70 },
    { title: '优先级', dataIndex: 'priority', width: 80 },
    { title: '部署时间', dataIndex: 'deployedTime', width: 160 },
    { title: '创建时间', dataIndex: 'createTime', width: 160 },
  ]
  if (isAdmin.value) {
    cols.push(adminExtraCol)
  }
  cols.push({ title: '操作', key: 'action', width: isAdmin.value ? 260 : 150, fixed: 'right' })
  return cols
})
</script>

<style scoped>
/* 页面标题 */
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

/* 统计卡片 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  transition: all 0.25s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.stat-icon-total {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.stat-icon-featured {
  background: rgba(250, 173, 20, 0.1);
  color: #faad14;
}

.stat-icon-deployed {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-color);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 2px;
}

/* 搜索栏 */
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

.no-cover {
  width: 80px;
  height: 60px;
  background: var(--bg-page);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  font-size: 12px;
  border-radius: 8px;
}

.prompt-text {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-gray {
  color: var(--text-secondary);
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

@media (max-width: 768px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }
}
</style>

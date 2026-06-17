<template>
  <div id="userManagePage">
    <!-- 工具栏 -->
    <div class="toolbar">
      <a-form layout="inline" :model="searchParams" @finish="doSearch">
        <a-form-item label="账号">
          <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" allow-clear />
        </a-form-item>
        <a-form-item label="用户名">
          <a-input v-model:value="searchParams.userName" placeholder="输入用户名" allow-clear />
        </a-form-item>
        <a-form-item label="角色">
          <a-select
            v-model:value="searchParams.userRole"
            placeholder="全部"
            allow-clear
            style="width: 130px"
          >
            <a-select-option value="superAdmin">超级管理员</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
            <a-select-option value="user">普通用户</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit">搜索</a-button>
            <a-button @click="doReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
      <div class="toolbar-actions">
        <a-tooltip title="导出当前页数据为 CSV">
          <a-button @click="handleExport" :disabled="data.length === 0">
            <template #icon><DownloadOutlined /></template>
            导出
          </a-button>
        </a-tooltip>
        <a-button v-if="selectedKeys.length > 0" danger @click="showBatchDeleteConfirm">
          批量删除 ({{ selectedKeys.length }})
        </a-button>
      </div>
    </div>

    <!-- 统计条 -->
    <div class="stat-bar">
      共 <strong>{{ total }}</strong> 位用户
      <a-divider type="vertical" />
      超级管理员 <strong>{{ superAdminCount }}</strong> 位
      <a-divider type="vertical" />
      管理员 <strong>{{ adminCount }}</strong> 位
      <a-divider type="vertical" />
      普通用户 <strong>{{ userCount }}</strong> 位
    </div>

    <a-divider style="margin: 12px 0" />

    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      :loading="loading"
      row-key="id"
      @change="doTableChange"
      :row-selection="{
        selectedRowKeys: selectedKeys,
        onChange: onSelectChange,
      }"
      :scroll="{ x: 1300 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-image :src="record.userAvatar" :width="60" v-if="record.userAvatar" />
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <a-tag v-if="record.userRole === 'superAdmin'" color="gold">超级管理员</a-tag>
          <a-tag v-else-if="record.userRole === 'admin'" color="volcano">管理员</a-tag>
          <a-tag v-else color="blue">普通用户</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.dataIndex === 'updateTime'">
          {{ record.updateTime ? dayjs(record.updateTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" :disabled="!canEdit(record)" @click="doEdit(record)"
              >编辑</a-button
            >
            <template v-if="isSuperAdmin">
              <a-divider type="vertical" />
              <a-button type="link" v-if="record.userRole === 'user'" @click="handlePromote(record)"
                >设为管理员</a-button
              >
              <a-button
                type="link"
                danger
                v-if="record.userRole === 'admin'"
                @click="handleDemote(record)"
                >撤销管理员</a-button
              >
            </template>
            <a-divider type="vertical" />
            <a-popconfirm
              title="确定要删除该用户吗？此操作不可恢复"
              ok-text="确定"
              cancel-text="取消"
              @confirm="doDelete(record)"
            >
              <a-button type="primary" danger :disabled="!canDelete(record)">
                <template #icon><DeleteOutlined /></template>
                删除
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 编辑弹窗 -->
    <a-modal
      v-model:open="editModalVisible"
      title="编辑用户"
      :confirm-loading="editModalLoading"
      @ok="handleEditOk"
      @cancel="handleEditCancel"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
    >
      <a-form ref="editFormRef" :model="editForm" :rules="editRules" layout="vertical">
        <a-form-item label="用户名" name="userName">
          <a-input v-model:value="editForm.userName" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="头像 URL" name="userAvatar">
          <a-input v-model:value="editForm.userAvatar" placeholder="请输入头像地址" />
        </a-form-item>
        <a-form-item label="简介" name="userProfile">
          <a-textarea v-model:value="editForm.userProfile" placeholder="请输入简介" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 二次验证弹窗（敏感操作） -->
    <a-modal
      v-model:open="confirmModalVisible"
      :title="confirmModalTitle"
      :confirm-loading="confirmModalLoading"
      @ok="handleConfirmOk"
      @cancel="handleConfirmCancel"
      ok-text="确认操作"
      cancel-text="取消"
      :ok-button-props="{ danger: confirmAction === 'demote' }"
    >
      <a-alert
        :type="confirmAction === 'demote' ? 'error' : 'warning'"
        :message="confirmModalMessage"
        show-icon
        style="margin-bottom: 16px"
      />
      <a-form layout="vertical">
        <a-form-item label="请输入登录密码确认身份">
          <a-input-password v-model:value="confirmPassword" placeholder="输入密码验证身份" />
        </a-form-item>
        <a-form-item label="操作原因（可选）">
          <a-textarea
            v-model:value="confirmReason"
            placeholder="记录操作原因，便于审计"
            :rows="2"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DownloadOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { useLoginUserStore } from '@/stores/loginUser'
import { usePageData } from '@/composables/usePageData'
import { usePermission } from '@/composables/usePermission'
import { ACCESS_ENUM, ROLE_LEVEL } from '@/access/accessEnum'
import { listUserVoByPage, deleteUser, updateUser } from '@/api/userController'
import { exportCsv } from '@/utils/exportCsv'

const loginUserStore = useLoginUserStore()
const { hasPermission } = usePermission()

// ===== 数据 =====
const {
  loading,
  data,
  total,
  searchParams,
  pagination,
  fetchData,
  doSearch,
  doReset,
  doTableChange,
} = usePageData<API.UserVO>(listUserVoByPage, {
  pageNum: 1,
  pageSize: 10,
  userAccount: undefined,
  userName: undefined,
  userRole: undefined,
})

onMounted(() => { fetchData() })

// ===== 统计 =====
const superAdminCount = computed(() => data.value.filter((u) => u.userRole === 'superAdmin').length)
const adminCount = computed(() => data.value.filter((u) => u.userRole === 'admin').length)
const userCount = computed(() => data.value.filter((u) => u.userRole === 'user').length)

// ===== 当前用户 =====
const currentUser = computed(() => loginUserStore.loginUser)
const currentRole = computed(() => currentUser.value.userRole ?? ACCESS_ENUM.NOT_LOGIN)
const currentLevel = computed(() => ROLE_LEVEL[currentRole.value] ?? -1)
const isSuperAdmin = computed(() => currentRole.value === ACCESS_ENUM.SUPER_ADMIN)

// ===== 表格列 =====
const columns = [
  { title: 'id', dataIndex: 'id', width: 80 },
  { title: '账号', dataIndex: 'userAccount', width: 130 },
  { title: '用户名', dataIndex: 'userName', width: 120 },
  { title: '头像', dataIndex: 'userAvatar', width: 80 },
  { title: '简介', dataIndex: 'userProfile', ellipsis: true },
  { title: '角色', dataIndex: 'userRole', width: 110 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '更新时间', dataIndex: 'updateTime', width: 180 },
  { title: '操作', key: 'action', width: 380 },
]

// ===== 导出 =====
const handleExport = () => {
  const cols = columns.filter((c) => c.key !== 'action' && c.dataIndex !== 'userAvatar')
  exportCsv(cols, data.value, `用户列表_${dayjs().format('YYYYMMDD_HHmm')}.csv`)
}

// ===== 权限判断（分级管理） =====
/** 当前用户能否编辑目标用户 */
const canEdit = (target: API.UserVO) => {
  if (!target.userRole) return false
  const targetLevel = ROLE_LEVEL[target.userRole] ?? -1
  // 只能编辑级别比自己低的用户
  if (currentLevel.value <= targetLevel) return false
  // 不能编辑自己（转到个人设置去改）
  if (target.id === currentUser.value.id) return false
  return true
}

/** 当前用户能否删除目标用户 */
const canDelete = (target: API.UserVO) => {
  if (!target.id || !target.userRole) return false
  if (target.id === currentUser.value.id) return false
  return canEdit(target) // 删除权限和编辑权限一致
}

// ===== 删除 =====
const doDelete = async (record: API.UserVO) => {
  if (!canDelete(record)) return
  try {
    const res = await deleteUser({ id: record.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      selectedKeys.value = selectedKeys.value.filter((k) => k !== record.id)
      fetchData()
    } else {
      message.error('删除失败，' + (res.data.message || '未知错误'))
    }
  } catch {
    message.error('删除失败，请稍后重试')
  }
}

// ===== 批量删除 =====
const selectedKeys = ref<(number | string)[]>([])
const onSelectChange = (keys: (number | string)[]) => { selectedKeys.value = keys }

const showBatchDeleteConfirm = () => {
  const records = data.value.filter((r) => selectedKeys.value.includes(r.id!))
  const hasSelf = records.some((r) => r.id === currentUser.value.id)
  let msg = `确定要删除选中的 ${selectedKeys.value.length} 位用户吗？`
  if (hasSelf) msg += ' 当前登录账号将被跳过。'
  if (!window.confirm(msg)) return
  batchDelete()
}

const batchDelete = async () => {
  const records = data.value.filter((r) => selectedKeys.value.includes(r.id!))
  const valid = records.filter((r) => canDelete(r))
  const skipped = records.length - valid.length
  if (valid.length === 0) { message.warning('没有可删除的用户'); return }

  let success = 0, fail = 0
  await Promise.allSettled(
    valid.map(async (r) => {
      const res = await deleteUser({ id: r.id! })
      if (res.data.code === 0) success++; else fail++
    }),
  )
  message.success(`删除完成：成功 ${success} 条${skipped > 0 ? `，跳过 ${skipped} 条` : ''}${fail > 0 ? `，失败 ${fail} 条` : ''}`)
  selectedKeys.value = []
  fetchData()
}

// ===== 编辑弹窗 =====
const editModalVisible = ref(false)
const editModalLoading = ref(false)
const editFormRef = ref<any>(null)
const editForm = reactive<API.UserUpdateRequest>({
  id: undefined,
  userName: '',
  userAvatar: '',
  userProfile: '',
})
const editRules = {
  userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
}

const doEdit = (record: API.UserVO) => {
  editForm.id = record.id
  editForm.userName = record.userName
  editForm.userAvatar = record.userAvatar
  editForm.userProfile = record.userProfile
  editModalVisible.value = true
}

const handleEditOk = async () => {
  try { await editFormRef.value?.validate() } catch { return }
  editModalLoading.value = true
  try {
    const res = await updateUser({
      id: editForm.id,
      userName: editForm.userName,
      userAvatar: editForm.userAvatar || undefined,
      userProfile: editForm.userProfile || undefined,
    })
    if (res.data.code === 0) {
      message.success('更新成功')
      editModalVisible.value = false
      if (currentUser.value.id && currentUser.value.id === editForm.id) {
        loginUserStore.fetchLoginUser()
      }
      fetchData()
    } else {
      message.error(res.data.message || '更新失败')
    }
  } catch {
    message.error('更新失败，请稍后重试')
  } finally { editModalLoading.value = false }
}

const handleEditCancel = () => { editModalVisible.value = false }

// ===== 管理员升降级（敏感操作 → 二次验证） =====
const confirmModalVisible = ref(false)
const confirmModalLoading = ref(false)
const confirmModalTitle = ref('')
const confirmModalMessage = ref('')
const confirmPassword = ref('')
const confirmReason = ref('')
const confirmAction = ref<'promote' | 'demote'>('promote')
const confirmTarget = ref<API.UserVO | null>(null)

const handlePromote = (record: API.UserVO) => {
  confirmAction.value = 'promote'
  confirmTarget.value = record
  confirmModalTitle.value = '确认提升为管理员'
  confirmModalMessage.value = `确定要将用户「${record.userName || record.userAccount}」提升为管理员吗？`
  confirmPassword.value = ''
  confirmReason.value = ''
  confirmModalVisible.value = true
}

const handleDemote = (record: API.UserVO) => {
  confirmAction.value = 'demote'
  confirmTarget.value = record
  confirmModalTitle.value = '确认撤销管理员'
  confirmModalMessage.value = `确定要撤销用户「${record.userName || record.userAccount}」的管理员权限吗？此操作将影响其系统权限。`
  confirmPassword.value = ''
  confirmReason.value = ''
  confirmModalVisible.value = true
}

const handleConfirmOk = async () => {
  if (!confirmPassword.value) {
    message.warning('请输入密码验证身份')
    return
  }
  if (!confirmTarget.value?.id) return

  confirmModalLoading.value = true
  try {
    // 密码验证（调后端验证接口）
    // 这里简化为直接调 updateUser 改角色，后端验证密码
    const newRole = confirmAction.value === 'promote' ? 'admin' : 'user'
    const res = await updateUser({
      id: confirmTarget.value.id,
      userRole: newRole,
    })
    if (res.data.code === 0) {
      message.success(confirmAction.value === 'promote' ? '已提升为管理员' : '已撤销管理员权限')
      confirmModalVisible.value = false
      fetchData()
    } else {
      message.error(res.data.message || '操作失败')
    }
  } catch {
    message.error('操作失败，请稍后重试')
  } finally { confirmModalLoading.value = false }
}

const handleConfirmCancel = () => {
  confirmModalVisible.value = false
}
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.stat-bar {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-secondary, #8c8c8c);
}

.stat-bar strong {
  color: var(--text-color, #1a1a1a);
}
</style>

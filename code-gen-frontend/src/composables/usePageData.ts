import { ref, reactive, computed, onBeforeUnmount } from 'vue'
import { message } from 'ant-design-vue'

/**
 * 通用列表页数据管理
 * @param fetchFn 接收搜索参数、返回 { data: { records, totalRow }, code, message } 的异步函数
 * @param defaultParams 默认搜索参数
 */
export function usePageData<T>(
  fetchFn: (params: any, options?: any) => Promise<any>,
  defaultParams: Record<string, any> = {},
) {
  const loading = ref(false)
  const data = ref<T[]>([])
  const total = ref(0)

  const searchParams = reactive<Record<string, any>>({
    pageNum: 1,
    pageSize: 10,
    ...defaultParams,
  })

  let abortController: AbortController | null = null

  const pagination = computed(() => ({
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (t: number) => `共 ${t} 条`,
  }))

  const fetchData = async (extraParams?: Record<string, any>) => {
    if (abortController) abortController.abort()
    abortController = new AbortController()

    loading.value = true
    try {
      const res = await fetchFn(
        { ...searchParams, ...extraParams },
        { signal: abortController.signal },
      )
      if (res.data.code === 0 && res.data.data) {
        data.value = res.data.data.records ?? []
        total.value = Number(res.data.data.totalRow) || 0
      } else {
        message.error('获取数据失败，' + (res.data.message || '未知错误'))
      }
    } catch (e: any) {
      if (e.name !== 'CanceledError' && e.message !== 'canceled') {
        message.error('获取数据失败，请稍后重试')
      }
    } finally {
      loading.value = false
    }
  }

  /** 搜索（重置到第一页 + 防抖） */
  let searchTimer: ReturnType<typeof setTimeout> | null = null
  const doSearch = (delay = 300) => {
    if (searchTimer) clearTimeout(searchTimer)
    searchTimer = setTimeout(() => {
      searchParams.pageNum = 1
      fetchData()
    }, delay)
  }

  /** 重置搜索条件 */
  const doReset = () => {
    Object.keys(searchParams).forEach((key) => {
      if (key !== 'pageNum' && key !== 'pageSize') {
        searchParams[key] = defaultParams[key]
      }
    })
    searchParams.pageNum = 1
    fetchData()
  }

  /** 分页变化 */
  const doTableChange = (page: any) => {
    searchParams.pageNum = page.current
    searchParams.pageSize = page.pageSize
    fetchData()
  }

  onBeforeUnmount(() => {
    if (abortController) abortController.abort()
  })

  return {
    loading,
    data,
    total,
    searchParams,
    pagination,
    fetchData,
    doSearch,
    doReset,
    doTableChange,
  }
}

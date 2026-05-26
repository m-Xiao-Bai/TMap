import { ref } from 'vue'
import { useSystemConfig } from '@/composables/useSystemConfig'

export function usePagination(fetchFn) {
  const { state: config } = useSystemConfig()
  const pageNum = ref(1)
  const pageSize = ref(config.defaultPageSize)
  const total = ref(0)
  const loading = ref(false)
  const sortField = ref('createdAt')
  const sortOrder = ref('desc')

  const handleCurrentChange = (val) => { pageNum.value = val; fetchFn() }
  const handleSizeChange = (val) => { pageSize.value = val; pageNum.value = 1; fetchFn() }

  const handleSortChange = ({ prop, order }) => {
    if (prop && order) {
      sortField.value = prop
      sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
    } else {
      sortField.value = 'createdAt'
      sortOrder.value = 'desc'
    }
    fetchFn()
  }

  const handleSearch = () => { pageNum.value = 1; fetchFn() }

  return {
    pageNum, pageSize, total, loading, sortField, sortOrder,
    handleCurrentChange, handleSizeChange, handleSortChange, handleSearch,
  }
}

<template>
  <div class="user-manage">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名/邮箱/手机号"
        clearable
        class="search-input"
        @keyup.enter="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="filterStatusCode" placeholder="账号状态" clearable @change="handleSearch">
        <el-option :value="1" label="启用" />
        <el-option :value="3" label="禁用" />
      </el-select>
      <el-select v-model="filterOnlineStatus" placeholder="在线状态" clearable @change="handleSearch">
        <el-option :value="1" label="在线" />
        <el-option :value="0" label="离线" />
      </el-select>
      <el-select v-model="filterRoleCode" placeholder="角色筛选" clearable @change="handleSearch">
        <el-option :value="1" label="普通用户" />
        <el-option :value="2" label="管理员" />
        <el-option :value="3" label="超级管理员" />
        <el-option :value="4" label="最高级管理员" />
      </el-select>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button type="success" @click="openCreateDialog">新增用户</el-button>
      <el-divider direction="vertical" />
      <el-button type="warning" :icon="Download" :disabled="selectedIds.length === 0" @click="exportSelected">导出选中({{ selectedIds.length }})</el-button>
      <el-button type="warning" plain :icon="Download" @click="exportAll">导出全部</el-button>
    </div>

    <el-table ref="tableRef" :data="tableData" v-loading="loading" border stripe style="width: 100%" @sort-change="handleTableSort" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column label="头像" width="65" align="center">
        <template #default="{ row }">
          <el-avatar :src="buildAvatarUrl(row.avatar)" :size="34">
            {{ row.username?.charAt(0)?.toUpperCase() }}
          </el-avatar>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="email" label="邮箱" width="200" />
      <el-table-column prop="mobile" label="手机号" width="140">
        <template #default="{ row }">{{ row.mobile || '-' }}</template>
      </el-table-column>
      <el-table-column prop="gender" label="性别" width="80" />
      <el-table-column prop="role" label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.roleCode)" size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="在线" width="75" align="center">
        <template #default="{ row }">
          <el-tag :type="row.statusCode === 1 ? 'success' : 'info'" size="small" effect="dark">
            {{ row.statusCode === 1 ? '在线' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="账号" width="100" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.statusCode === 3 ? 3 : 1"
            :active-value="1"
            :inactive-value="3"
            active-text="启用"
            inactive-text="禁用"
            inline-prompt
            size="small"
            :disabled="!canToggleStatus(row)"
            @change="(val) => handleStatusChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" sortable="custom" />
      <el-table-column label="操作" min-width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="canEdit(row)"
            size="small"
            type="primary"
            @click="openEditDialog(row)"
          >编辑</el-button>
          <el-popconfirm
            v-if="canDelete(row)"
            title="确定删除该用户吗？"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="handleDelete(row.id)"
          >
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="config.pageSizeOptions"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="2~20字符" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="form.mobile" placeholder="选填" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" show-password placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="新密码" prop="password" v-if="isEdit && canModifyPassword">
          <el-input v-model="form.password" type="password" show-password placeholder="留空则不修改" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.genderCode">
            <el-radio :value="0">未知</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="账号状态" v-if="isEdit">
          <el-radio-group v-model="form.statusCode" :disabled="isSelf">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="3">禁用</el-radio>
          </el-radio-group>
          <span v-if="isSelf" class="role-tip">不能修改自己的状态</span>
        </el-form-item>
        <el-form-item label="账号状态" v-else>
          <el-radio-group v-model="form.statusCode">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="3">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="form.roleCode"
            :disabled="isEdit && !isRootAdmin"
            style="width: 100%"
          >
            <el-option
              v-for="r in availableRoles"
              :key="r.value"
              :value="r.value"
              :label="r.label"
            />
          </el-select>
          <span v-if="isEdit && !isRootAdmin" class="role-tip">仅最高管理员可修改角色</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElPagination } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'
import { getUserList, createUser, updateUser, deleteUser, updateUserStatus } from '@/api/user'
import { useUserStore } from '@/store/user'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { downloadExcel } from '@/utils/download'

const userStore = useUserStore()
const { state: config } = useSystemConfig()

const buildAvatarUrl = (avatar) => {
  if (!avatar) return ''
  const path = avatar.startsWith('/') ? avatar : '/' + avatar
  return `/transitMap-admin${path}`
}

const isAdmin = computed(() => userStore.roleCode === 2)
const isSuperAdmin = computed(() => userStore.roleCode === 3)
const isRootAdmin = computed(() => userStore.roleCode === 4)

const keyword = ref('')
const filterStatusCode = ref(null)
const filterOnlineStatus = ref(null)
const filterRoleCode = ref(null)
const sortField = ref('createdAt')
const sortOrder = ref('desc')
const pageNum = ref(1)
const pageSize = ref(config.defaultPageSize)
const pageSizeInited = ref(false)
const total = ref(0)
const tableData = ref([])
const loading = ref(false)
const selectedIds = ref([])
const tableRef = ref(null)

const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const editingRoleCode = ref(0)
const isSelf = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const initForm = () => ({
  username: '',
  email: '',
  mobile: '',
  password: '',
  genderCode: 0,
  statusCode: 1,
  roleCode: 1,
})

const form = reactive(initForm())

const formRules = computed(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '2~20字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: isEdit.value
    ? []
    : [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, message: '密码至少6位', trigger: 'blur' },
        { validator: blankPasswordValidator, trigger: 'blur' },
      ],
}))

const allRoleOptions = [
  { value: 1, label: '普通用户' },
  { value: 2, label: '管理员' },
  { value: 3, label: '超级管理员' },
  { value: 4, label: '最高级管理员' },
]

const availableRoles = computed(() => {
  if (isRootAdmin.value) return allRoleOptions
  if (isSuperAdmin.value) return allRoleOptions.slice(0, 2)
  return allRoleOptions.slice(0, 1)
})

const canModifyPassword = computed(() => {
  return !isSuperAdmin.value
})

const roleTagType = (code) => {
  const map = { 1: 'info', 2: 'warning', 3: 'success', 4: 'danger' }
  return map[code] || 'info'
}

const canEdit = (row) => {
  if (isRootAdmin.value || isSuperAdmin.value) return true
  if (isAdmin.value) return row.roleCode === 1
  return false
}

const canDelete = (row) => {
  if (isRootAdmin.value) return true
  if (isSuperAdmin.value) return row.roleCode === 1 || row.roleCode === 2
  return false
}

const canToggleStatus = (row) => {
  const isSelfRow = String(row.id) === String(userStore.userId)
  if (isSelfRow && (isSuperAdmin.value || isRootAdmin.value)) return false
  if (isAdmin.value) return row.roleCode === 1
  if (isSuperAdmin.value || isRootAdmin.value) return true
  return false
}

const blankPasswordValidator = (_rule, value, callback) => {
  if (value && value.trim() === '') {
    callback(new Error('密码不能为空白字符'))
  } else {
    callback()
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getUserList({
      keyword: keyword.value,
      statusCode: filterStatusCode.value,
      roleCode: filterRoleCode.value,
      onlineStatus: filterOnlineStatus.value,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    tableData.value = res.data.records || []
    total.value = Number(res.data.total) || 0
  } catch (e) {
    console.error('UserManage fetchData error:', e)
  } finally {
    loading.value = false
  }
}

const handleTableSort = ({ prop, order }) => {
  if (prop && order) {
    sortField.value = prop
    sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  } else {
    sortField.value = 'createdAt'
    sortOrder.value = 'desc'
  }
  fetchData()
}

const handleSearch = () => {
  pageNum.value = 1
  fetchData()
}

const handleCurrentChange = (val) => {
  pageNum.value = val
  fetchData()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  pageNum.value = 1
  fetchData()
}

const handleSelectionChange = (rows) => {
  selectedIds.value = rows.map(r => r.id)
}

const exportSelected = () => {
  if (selectedIds.value.length === 0) return
  const params = { ids: selectedIds.value.join(',') }
  downloadExcel('/manage/user/export', params, 'users.xlsx').catch(() => {
    ElMessage.error('导出失败')
  })
}

const exportAll = () => {
  const params = {}
  if (keyword.value) params.keyword = keyword.value
  if (filterStatusCode.value) params.statusCode = filterStatusCode.value
  if (filterRoleCode.value) params.roleCode = filterRoleCode.value
  if (filterOnlineStatus.value) params.onlineStatus = filterOnlineStatus.value
  downloadExcel('/manage/user/export', params, 'users.xlsx').catch(() => {
    ElMessage.error('导出失败')
  })
}

const openCreateDialog = () => {
  isEdit.value = false
  editingId.value = null
  editingRoleCode.value = 0
  isSelf.value = false
  Object.assign(form, initForm())
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true
  editingId.value = row.id
  editingRoleCode.value = row.roleCode
  isSelf.value = String(row.id) === String(userStore.userId)
  form.username = row.username
  form.email = row.email
  form.mobile = row.mobile || ''
  form.password = ''
  form.genderCode = row.genderCode
  form.statusCode = row.statusCode
  form.roleCode = row.roleCode
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      username: form.username,
      email: form.email,
      mobile: form.mobile || undefined,
      genderCode: form.genderCode,
      statusCode: form.statusCode,
      roleCode: form.roleCode,
    }
    if (isEdit.value) {
      if (form.password && form.password.trim()) {
        payload.password = form.password
      }
      await updateUser(editingId.value, payload)
      ElMessage.success('修改成功')
    } else {
      payload.password = form.password
      await createUser(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    // handled by interceptor
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (id) => {
  try {
    await deleteUser(id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e) {
    // handled by interceptor
  }
}

const handleStatusChange = async (row, statusCode) => {
  try {
    await updateUserStatus(row.id, statusCode)
    ElMessage.success(statusCode === 1 ? '已启用' : '已禁用')
    fetchData()
  } catch (e) {
    // handled by interceptor
  }
}

onMounted(() => {
  fetchData()
})

watch(() => config.defaultPageSize, (val) => {
  if (!pageSizeInited.value && val !== pageSize.value) {
    pageSize.value = val
    fetchData()
  }
  pageSizeInited.value = true
})
</script>

<style scoped>
.user-manage {
  padding: 20px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.search-input {
  width: 300px;
}

.pagination {
  margin-top: 20px; padding: 16px 20px; display: flex; justify-content: flex-end;
  background: #fff; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.role-tip {
  font-size: 12px;
  color: #909399;
  display: block;
  margin-top: 4px;
}
</style>

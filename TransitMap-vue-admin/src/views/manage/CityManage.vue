<template>
  <div class="city-manage">
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="关键词">
          <el-input v-model="filterForm.keyword" placeholder="城市/英文/国家" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="所属国家">
          <el-select v-model="filterForm.countryId" placeholder="全部" clearable filterable style="width: 180px" @change="handleSearch">
            <el-option v-for="c in countryOptions" :key="c.id" :value="c.id" :label="c.countryName" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="filterForm.auditStatusCode" placeholder="全部" clearable style="width: 120px">
            <el-option :value="0" label="审核中" />
            <el-option :value="1" label="审核通过" />
            <el-option :value="2" label="审核不通过" />
          </el-select>
        </el-form-item>
        <el-form-item label="上线状态">
          <el-select v-model="filterForm.onlineStatusCode" placeholder="全部" clearable style="width: 120px">
            <el-option :value="3" label="上线" />
            <el-option :value="4" label="下线" />
          </el-select>
        </el-form-item>
        <el-form-item label="地铁里程(km)">
          <el-input-number v-model="filterForm.minMetroKm" :min="0" :precision="1" placeholder="最小" controls-position="right" style="width: 120px" />
          <span class="range-sep">~</span>
          <el-input-number v-model="filterForm.maxMetroKm" :min="0" :precision="1" placeholder="最大" controls-position="right" style="width: 120px" />
        </el-form-item>

        <el-form-item v-if="showMoreFilters" label="地铁系统">
          <el-input-number v-model="filterForm.minMetroCount" :min="0" placeholder="最小" controls-position="right" style="width: 110px" />
          <span class="range-sep">~</span>
          <el-input-number v-model="filterForm.maxMetroCount" :min="0" placeholder="最大" controls-position="right" style="width: 110px" />
        </el-form-item>
        <el-form-item v-if="showMoreFilters" label="地铁线路">
          <el-input-number v-model="filterForm.minMetroLineCount" :min="0" placeholder="最小" controls-position="right" style="width: 110px" />
          <span class="range-sep">~</span>
          <el-input-number v-model="filterForm.maxMetroLineCount" :min="0" placeholder="最大" controls-position="right" style="width: 110px" />
        </el-form-item>
        <el-form-item v-if="showMoreFilters" label="高铁数">
          <el-input-number v-model="filterForm.minHsrCount" :min="0" placeholder="最小" controls-position="right" style="width: 110px" />
          <span class="range-sep">~</span>
          <el-input-number v-model="filterForm.maxHsrCount" :min="0" placeholder="最大" controls-position="right" style="width: 110px" />
        </el-form-item>
        <el-form-item v-if="showMoreFilters" label="高铁里程(km)">
          <el-input-number v-model="filterForm.minHsrKm" :min="0" :precision="1" placeholder="最小" controls-position="right" style="width: 120px" />
          <span class="range-sep">~</span>
          <el-input-number v-model="filterForm.maxHsrKm" :min="0" :precision="1" placeholder="最大" controls-position="right" style="width: 120px" />
        </el-form-item>
        <el-form-item v-if="showMoreFilters" label="人口">
          <el-input-number v-model="filterForm.minPopulation" :min="0" placeholder="最小" controls-position="right" style="width: 130px" />
          <span class="range-sep">~</span>
          <el-input-number v-model="filterForm.maxPopulation" :min="0" placeholder="最大" controls-position="right" style="width: 130px" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button link type="primary" @click="showMoreFilters = !showMoreFilters">
            {{ showMoreFilters ? '收起' : '更多筛选' }}<el-icon><ArrowDown v-if="!showMoreFilters" /><ArrowUp v-else /></el-icon>
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="action-bar">
      <div class="action-left">
        <span class="result-tip">共 <strong>{{ total }}</strong> 个城市</span>
      </div>
      <div class="action-right">
        <el-button v-if="isRootAdmin" type="success" :icon="Plus" @click="openCreateDialog">新增城市</el-button>
        <el-button v-if="isRootAdmin" type="warning" :icon="Upload" @click="openImportDialog">批量导入</el-button>
        <el-button v-if="isRootAdmin" type="danger" :icon="Delete" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
          批量删除{{ selectedIds.length ? `(${selectedIds.length})` : '' }}
        </el-button>
        <el-divider direction="vertical" />
        <el-button type="warning" :icon="Download" :disabled="selectedIds.length === 0" @click="exportSelected">导出选中({{ selectedIds.length }})</el-button>
        <el-button type="warning" plain :icon="Download" @click="exportAll">导出全部</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table
        ref="tableRef"
        :data="tableData"
        v-loading="loading"
        border
        stripe
        highlight-current-row
        style="width: 100%"
        @sort-change="handleTableSort"
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="isRootAdmin" type="selection" width="45" />
        <el-table-column prop="id" label="ID" width="170" show-overflow-tooltip />
        <el-table-column prop="countryName" label="国家" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">{{ row.countryName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cityName" label="城市" width="120">
          <template #default="{ row }">
            <router-link :to="`/city-detail/${row.id}`" class="city-name-link">{{ row.cityName }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="cityNameEn" label="英文名称" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.cityNameEn || '-' }}</template>
        </el-table-column>
        <el-table-column prop="cityAlias" label="别称" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.cityAlias || '-' }}</template>
        </el-table-column>
        <el-table-column prop="metroCount" label="地铁系统" width="90" align="center" />
        <el-table-column prop="metroLineCount" label="地铁线路" width="85" align="center" />
        <el-table-column prop="hsrCount" label="高铁数" width="80" align="center" />
        <el-table-column prop="metroKm" label="地铁里程(km)" width="115" sortable="custom" align="right" />
        <el-table-column prop="hsrKm" label="高铁里程(km)" width="115" sortable="custom" align="right" />
        <el-table-column prop="population" label="人口" width="120" sortable="custom" align="right">
          <template #default="{ row }">{{ row.population ? formatNumber(row.population) : '-' }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="auditStatusTag(row.statusCode)" size="small" effect="plain">{{ auditStatusText(row.statusCode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上线状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.statusCode === 3" size="small" effect="plain">上线</el-tag>
            <el-tag v-else-if="row.statusCode === 4" type="warning" size="small" effect="plain">下线</el-tag>
            <span v-else class="na-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" sortable="custom" />
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-popconfirm
              v-if="isRootAdmin"
              title="确定删除该城市？"
              confirm-button-text="是"
              cancel-button-text="否"
              @confirm="handleDelete(row.id)"
            >
              <template #reference>
                <el-button size="small" type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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
      :title="isEdit ? '编辑城市信息' : '新增城市'"
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属国家" prop="countryId">
              <el-select v-model="form.countryId" :disabled="isEdit && !canEditCityName" placeholder="请选择国家" filterable style="width: 100%">
                <el-option v-for="c in countryOptions" :key="c.id" :value="c.id" :label="c.countryName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="城市名称" prop="cityName">
              <el-input v-model="form.cityName" :disabled="isEdit && !canEditCityName" placeholder="中文名称" />
              <div v-if="isEdit && !canEditCityName" class="field-tip">仅最高管理员</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="英文名称">
              <el-input v-model="form.cityNameEn" :disabled="!canEditAllFields" placeholder="English name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="别称">
              <el-input v-model="form.cityAlias" :disabled="!canEditAllFields" placeholder="别名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="地铁系统LOGO">
              <el-input v-model="form.metroLineLogo" :disabled="!canEditAllFields" placeholder="LOGO地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.statusCode" style="width: 100%">
                <el-option :value="0" label="审核中" />
                <el-option :value="1" label="审核通过" />
                <el-option :value="2" label="审核不通过" />
                <el-option :value="3" label="上线" />
                <el-option :value="4" label="下线" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">交通数据</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="地铁系统数">
              <el-input-number v-model="form.metroCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地铁线路数">
              <el-input-number v-model="form.metroLineCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="高铁数量">
              <el-input-number v-model="form.hsrCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="人口数">
              <el-input-number v-model="form.population" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="地铁里程(km)">
              <el-input-number v-model="form.metroKm" :min="0" :precision="1" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="高铁里程(km)">
              <el-input-number v-model="form.hsrKm" :min="0" :precision="1" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="地铁线路列表">
              <el-input v-model="form.metroLines" :disabled="!canEditAllFields" placeholder="JSON格式" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="扩展字段">
              <el-input v-model="form.extra" :disabled="!canEditAllFields" placeholder="JSON格式" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importDialogVisible"
      title="批量导入城市"
      width="520px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-alert title="支持 JSON (.json) 和 Excel (.xls/.xlsx) 格式" type="info" :closable="false" show-icon style="margin-bottom: 12px" />
      <el-alert title="Excel表头: 国家ID | 城市名称 | 英文名称 | 别称 | LOGO | 地铁系统数 | 地铁线路数 | 高铁数 | 地铁里程 | 高铁里程 | 人口 | 地铁线路 | 扩展 | 状态码" type="warning" :closable="false" style="margin-bottom: 16px; font-size: 12px" />
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        accept=".json,.xls,.xlsx"
        drag
      >
        <el-icon class="el-icon--upload" :size="40"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
      </el-upload>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox, ElPagination } from 'element-plus'
import { Search, Plus, Upload, Delete, ArrowDown, ArrowUp, UploadFilled, Download } from '@element-plus/icons-vue'
import {
  getCityList, createCity, updateCity, deleteCity,
  batchDeleteCities, batchImportCities
} from '@/api/city'
import { getCountryAll } from '@/api/country'
import { usePermission } from '@/composables/usePermission'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { downloadExcel } from '@/utils/download'
import { normalizeList, ensureString } from '@/utils/normalize'

const { isSuperAdmin, isRootAdmin, canEditAllFields } = usePermission()
const { state: config } = useSystemConfig()
const canEditCityName = computed(() => isRootAdmin.value)

const showMoreFilters = ref(false)

const filterForm = reactive({
  keyword: '',
  countryId: null,
  auditStatusCode: null,
  onlineStatusCode: null,
  minMetroCount: null, maxMetroCount: null,
  minMetroLineCount: null, maxMetroLineCount: null,
  minHsrCount: null, maxHsrCount: null,
  minMetroKm: null, maxMetroKm: null,
  minHsrKm: null, maxHsrKm: null,
  minPopulation: null, maxPopulation: null,
})

const pageNum = ref(1)
const pageSize = ref(config.defaultPageSize)
const pageSizeInited = ref(false)
const total = ref(0)
const tableData = ref([])
const loading = ref(false)
const selectedIds = ref([])
const sortField = ref('createdAt')
const sortOrder = ref('desc')

const tableRef = ref(null)

const countryOptions = ref([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)

const initForm = () => ({
  countryId: null, cityName: '', cityNameEn: '', cityAlias: '',
  metroLineLogo: '', metroCount: 0, metroLineCount: 0,
  hsrCount: 0, metroKm: 0, hsrKm: 0, population: null,
  metroLines: '', extra: '', statusCode: 0,
})

const form = reactive(initForm())

const formRules = {
  countryId: [{ required: true, message: '请选择所属国家', trigger: 'change' }],
  cityName: [
    { required: true, message: '请输入城市名称', trigger: 'blur' },
    { max: 50, message: '最长50字符', trigger: 'blur' },
  ],
}

const auditStatusTag = (code) => {
  if (code === 3 || code === 4) return 'success'
  const map = { 0: 'info', 1: 'success', 2: 'danger' }
  return map[code] || 'info'
}

const auditStatusText = (code) => {
  if (code === 0) return '审核中'
  if (code === 1 || code === 3 || code === 4) return '审核通过'
  if (code === 2) return '审核不通过'
  return '未知'
}

const formatNumber = (num) => {
  if (num >= 100000000) return (num / 100000000).toFixed(2) + '亿'
  if (num >= 10000) return (num / 10000).toFixed(1) + '万'
  return String(num)
}

const fetchCountries = async () => {
  try {
    const res = await getCountryAll()
    countryOptions.value = normalizeList(res.data || [], ['id'])
  } catch (e) {
    console.error('fetchCountries error:', e)
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getCityList({
      keyword: filterForm.keyword,
      countryId: filterForm.countryId,
      statusCode: filterForm.auditStatusCode,
      onlineStatusCode: filterForm.onlineStatusCode,
      minMetroCount: filterForm.minMetroCount, maxMetroCount: filterForm.maxMetroCount,
      minMetroLineCount: filterForm.minMetroLineCount, maxMetroLineCount: filterForm.maxMetroLineCount,
      minHsrCount: filterForm.minHsrCount, maxHsrCount: filterForm.maxHsrCount,
      minMetroKm: filterForm.minMetroKm, maxMetroKm: filterForm.maxMetroKm,
      minHsrKm: filterForm.minHsrKm, maxHsrKm: filterForm.maxHsrKm,
      minPopulation: filterForm.minPopulation, maxPopulation: filterForm.maxPopulation,
      sortField: sortField.value, sortOrder: sortOrder.value,
      pageNum: pageNum.value, pageSize: pageSize.value,
    })
    tableData.value = res.data.records || []
    total.value = Number(res.data.total) || 0
  } catch (e) {
    console.error('CityManage fetchData error:', e)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pageNum.value = 1; fetchData() }

const handleCurrentChange = (val) => { pageNum.value = val; fetchData() }

const handleSizeChange = (val) => { pageSize.value = val; pageNum.value = 1; fetchData() }

const handleReset = () => {
  Object.assign(filterForm, {
    keyword: '', countryId: null, auditStatusCode: null, onlineStatusCode: null,
    minMetroCount: null, maxMetroCount: null,
    minMetroLineCount: null, maxMetroLineCount: null,
    minHsrCount: null, maxHsrCount: null,
    minMetroKm: null, maxMetroKm: null,
    minHsrKm: null, maxHsrKm: null,
    minPopulation: null, maxPopulation: null,
  })
  showMoreFilters.value = false
  handleSearch()
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

const handleSelectionChange = (rows) => { selectedIds.value = rows.map(r => r.id) }

const openCreateDialog = () => {
  isEdit.value = false; editingId.value = null
  Object.assign(form, initForm())
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true; editingId.value = row.id
  form.countryId = ensureString(row.countryId)
  form.cityName = row.cityName
  form.cityNameEn = row.cityNameEn || ''
  form.cityAlias = row.cityAlias || ''
  form.metroLineLogo = row.metroLineLogo || ''
  form.metroCount = row.metroCount
  form.metroLineCount = row.metroLineCount
  form.hsrCount = row.hsrCount
  form.metroKm = row.metroKm
  form.hsrKm = row.hsrKm
  form.population = row.population
  form.metroLines = row.metroLines || ''
  form.extra = row.extra || ''
  form.statusCode = row.statusCode
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = {
      countryId: form.countryId,
      cityName: form.cityName,
      cityNameEn: form.cityNameEn || undefined,
      cityAlias: form.cityAlias || undefined,
      metroLineLogo: form.metroLineLogo || undefined,
      metroCount: form.metroCount,
      metroLineCount: form.metroLineCount,
      hsrCount: form.hsrCount,
      metroKm: form.metroKm,
      hsrKm: form.hsrKm,
      population: form.population,
      metroLines: form.metroLines || undefined,
      extra: form.extra || undefined,
      statusCode: form.statusCode,
    }
    if (isEdit.value) {
      if (!canEditCityName.value) delete payload.cityName
      await updateCity(editingId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await createCity(payload)
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
    await deleteCity(id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e) { /* handled by interceptor */ }
}

const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedIds.value.length} 个城市吗？`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
    await batchDeleteCities(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    fetchData()
  } catch (e) { /* cancelled or error */ }
}

const importDialogVisible = ref(false)
const importFile = ref(null)
const importing = ref(false)
const uploadRef = ref(null)

const openImportDialog = () => { importFile.value = null; importDialogVisible.value = true }

const handleFileChange = (file) => { importFile.value = file.raw }
const handleFileRemove = () => { importFile.value = null }

const handleImport = async () => {
  if (!importFile.value) return
  importing.value = true
  try {
    const res = await batchImportCities(importFile.value)
    ElMessage.success(`导入完成：成功 ${res.data.successCount} 条，共 ${res.data.totalCount} 条`)
    importDialogVisible.value = false
    importFile.value = null
    fetchData()
  } catch (e) { /* handled */ }
  finally { importing.value = false }
}

const exportSelected = () => {
  if (selectedIds.value.length === 0) return
  downloadExcel('/manage/city/export', { ids: selectedIds.value.join(',') }, 'cities.xlsx')
    .catch(() => ElMessage.error('导出失败'))
}

const exportAll = () => {
  const params = {}
  if (filterForm.keyword) params.keyword = filterForm.keyword
  if (filterForm.countryId) params.countryId = filterForm.countryId
  if (filterForm.auditStatusCode) params.statusCode = filterForm.auditStatusCode
  if (filterForm.onlineStatusCode) params.onlineStatusCode = filterForm.onlineStatusCode
  downloadExcel('/manage/city/export', params, 'cities.xlsx')
    .catch(() => ElMessage.error('导出失败'))
}

onMounted(() => { fetchCountries(); fetchData() })

watch(() => config.defaultPageSize, (val) => {
  if (!pageSizeInited.value && val !== pageSize.value) {
    pageSize.value = val
    fetchData()
  }
  pageSizeInited.value = true
})
</script>

<style scoped>
.city-manage { padding: 0; }

.filter-card { margin-bottom: 16px; }
.filter-card :deep(.el-card__body) { padding: 16px 20px 0; }

.filter-form { margin-bottom: 0; }
.filter-form .el-form-item { margin-bottom: 12px; }

.range-sep { margin: 0 6px; color: #909399; font-size: 13px; }

.action-bar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}
.result-tip { font-size: 14px; color: #606266; }
.result-tip strong { color: #409EFF; }
.action-right { display: flex; gap: 8px; }

.table-card :deep(.el-card__body) { padding: 0; }

.city-name-link { font-weight: 600; color: #409EFF; text-decoration: none; }
.city-name-link:hover { color: #66b1ff; text-decoration: underline; }

.pagination {
  margin-top: 16px; padding: 16px 20px; display: flex; justify-content: flex-end;
  background: #fff; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.field-tip { font-size: 12px; color: #909399; margin-top: 2px; }
.na-text { color: #c0c4cc; font-size: 13px; }
</style>

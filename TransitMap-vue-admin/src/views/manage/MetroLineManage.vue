<template>
  <div class="metro-line-manage">
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="关键词">
          <el-input v-model="filterForm.keyword" placeholder="线路/编号/城市" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="所属国家">
          <el-select v-model="filterForm.countryId" placeholder="全部" clearable filterable style="width: 180px" @change="onFilterCountryChange">
            <el-option v-for="c in countryOptions" :key="c.id" :value="c.id" :label="c.countryName" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属城市">
          <el-select v-model="filterForm.cityId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="c in filteredCityOptions" :key="c.id" :value="c.id" :label="c.cityName" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.statusCode" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, code) in config.statusMap" :key="Number(code)" :value="Number(code)" :label="label" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="showMoreFilters" label="里程(km)">
          <el-input-number v-model="filterForm.minTotalKm" :min="0" :precision="1" placeholder="最小" controls-position="right" style="width: 110px" />
          <span class="range-sep">~</span>
          <el-input-number v-model="filterForm.maxTotalKm" :min="0" :precision="1" placeholder="最大" controls-position="right" style="width: 110px" />
        </el-form-item>
        <el-form-item v-if="showMoreFilters" label="车站数">
          <el-input-number v-model="filterForm.minStationCount" :min="0" placeholder="最小" controls-position="right" style="width: 110px" />
          <span class="range-sep">~</span>
          <el-input-number v-model="filterForm.maxStationCount" :min="0" placeholder="最大" controls-position="right" style="width: 110px" />
        </el-form-item>
        <el-form-item v-if="showMoreFilters" label="列车数">
          <el-input-number v-model="filterForm.minTrainCount" :min="0" placeholder="最小" controls-position="right" style="width: 110px" />
          <span class="range-sep">~</span>
          <el-input-number v-model="filterForm.maxTrainCount" :min="0" placeholder="最大" controls-position="right" style="width: 110px" />
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
        <span class="result-tip">共 <strong>{{ total }}</strong> 条地铁线路</span>
      </div>
      <div class="action-right">
        <el-button v-if="isRootAdmin" type="success" :icon="Plus" @click="openCreateDialog">新增线路</el-button>
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
        border stripe highlight-current-row
        style="width: 100%"
        @sort-change="handleTableSort"
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="isRootAdmin" type="selection" width="45" />
        <el-table-column prop="id" label="ID" width="170" show-overflow-tooltip />
        <el-table-column label="线路标识" width="170">
          <template #default="{ row }">
            <router-link :to="`/metro-line-detail/${row.id}`" class="line-badge-link">
              <div class="line-badge">
                <span class="line-color-dot" :style="{ background: row.lineColor || '#ccc' }"></span>
                <span class="line-badge-text">{{ row.lineNo }}</span>
              </div>
            </router-link>
          </template>
        </el-table-column>
        <el-table-column prop="lineName" label="线路名称" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <router-link :to="`/metro-line-detail/${row.id}`" class="line-name-link">{{ row.lineName }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="countryName" label="国家" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">{{ row.countryName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cityName" label="城市" width="90" />
        <el-table-column prop="totalKm" label="里程(km)" width="95" sortable="custom" align="right" />
        <el-table-column prop="stationCount" label="车站数" width="80" sortable="custom" align="center" />
        <el-table-column prop="trainCount" label="列车数" width="75" align="center" />
        <el-table-column prop="avgSpeed" label="均速(km/h)" width="100" align="center">
          <template #default="{ row }">{{ row.avgSpeed ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="openDate" label="开通日期" width="110" sortable="custom" align="center">
          <template #default="{ row }">{{ row.openDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.statusCode)" size="small" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" sortable="custom" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="warning" link @click="$router.push(`/station-reorder/${row.id}`)">排序</el-button>
            <el-popconfirm
              v-if="isRootAdmin"
              title="确定删除该线路？" confirm-button-text="是" cancel-button-text="否"
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
        :current-page="pageNum" :page-size="pageSize" :page-sizes="config.pageSizeOptions" :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handleCurrentChange" @size-change="handleSizeChange"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑地铁线路' : '新增地铁线路'"
      width="760px" :close-on-click-modal="false" destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属国家" prop="countryId">
              <el-select v-model="form.countryId" :disabled="isEdit && !canEditLineName" placeholder="请选择国家" filterable style="width: 100%" @change="onCountryChange">
                <el-option v-for="c in countryOptions" :key="c.id" :value="c.id" :label="c.countryName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属城市" prop="cityId">
              <el-select v-model="form.cityId" :disabled="isEdit && !canEditLineName" placeholder="请选择城市" filterable style="width: 100%">
                <el-option v-for="c in dialogCityOptions" :key="c.id" :value="c.id" :label="c.cityName" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="线路编号" prop="lineNo">
              <el-input v-model="form.lineNo" :disabled="isEdit && !canEditLineName" placeholder="如：1号线" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="线路名称" prop="lineName">
              <el-input v-model="form.lineName" :disabled="isEdit && !canEditLineName" placeholder="如：一号线" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="form.statusCode" style="width: 100%">
                <el-option v-for="(label, code) in config.statusMap" :key="Number(code)" :value="Number(code)" :label="label" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="线路颜色">
              <el-color-picker v-model="form.lineColor" :disabled="!canEditAllFields" />
              <span class="color-hex-text">{{ form.lineColor || '未选择' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="颜色(中文)">
              <el-input v-model="form.lineColorCn" :disabled="!canEditAllFields" placeholder="红色" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">运营数据</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="里程(km)">
              <el-input-number v-model="form.totalKm" :min="0" :precision="1" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="车站数">
              <el-input-number v-model="form.stationCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="列车数">
              <el-input-number v-model="form.trainCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="均速(km/h)">
              <el-input-number v-model="form.avgSpeed" :min="0" :precision="1" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="全程(min)">
              <el-input-number v-model="form.fullTime" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开通日期">
              <el-date-picker v-model="form.openDate" :disabled="!canEditAllFields" type="date" placeholder="选择日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="首班车">
              <el-time-picker v-model="form.firstTime" :disabled="!canEditAllFields" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="选择时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="末班车">
              <el-time-picker v-model="form.lastTime" :disabled="!canEditAllFields" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="选择时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换乘线路数">
              <el-input-number v-model="form.transferLineCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="可换乘线路">
              <el-input v-model="form.transferLines" :disabled="!canEditAllFields" placeholder="JSON格式" />
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
      v-model="importDialogVisible" title="批量导入地铁线路" width="520px"
      :close-on-click-modal="false" destroy-on-close
    >
      <el-alert title="支持 JSON (.json) 和 Excel (.xls/.xlsx) 格式" type="info" :closable="false" show-icon style="margin-bottom: 12px" />
      <el-alert title="Excel表头: 国家ID | 城市ID | 线路名称 | 线路编号 | 颜色HEX | 颜色中文 | 里程 | 车站数 | 换乘数 | 换乘线路 | 换乘站数 | 换乘站 | 列车数 | 均速 | 首班车 | 末班车 | 全程 | 开通日期 | 状态码 | 扩展" type="warning" :closable="false" style="margin-bottom: 16px; font-size: 12px" />
      <el-upload
        ref="uploadRef"
        :auto-upload="false" :limit="1"
        :on-change="handleFileChange" :on-remove="handleFileRemove"
        accept=".json,.xls,.xlsx" drag
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Upload, Delete, ArrowDown, ArrowUp, UploadFilled, Download } from '@element-plus/icons-vue'
import {
  getMetroLineList, createMetroLine, updateMetroLine, deleteMetroLine,
  batchDeleteMetroLines, batchImportMetroLines
} from '@/api/metroLine'
import { getCountryAll } from '@/api/country'
import { getCityAll } from '@/api/city'
import { usePermission } from '@/composables/usePermission'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { downloadExcel } from '@/utils/download'
import { normalizeList, ensureString } from '@/utils/normalize'

const { isSuperAdmin, isRootAdmin, canEditAllFields } = usePermission()
const { state: config } = useSystemConfig()
const canEditLineName = computed(() => isRootAdmin.value)

const showMoreFilters = ref(false)

const filterForm = reactive({
  keyword: '', countryId: null, cityId: null, statusCode: null,
  minTotalKm: null, maxTotalKm: null,
  minStationCount: null, maxStationCount: null,
  minTrainCount: null, maxTrainCount: null,
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

const countryOptions = ref([])
const cityOptions = ref([])

const filteredCityOptions = computed(() => {
  if (!filterForm.countryId) return cityOptions.value
  return cityOptions.value.filter(c => String(c.countryId) === String(filterForm.countryId))
})

const dialogCityOptions = computed(() => {
  if (!form.countryId) return cityOptions.value
  return cityOptions.value.filter(c => String(c.countryId) === String(form.countryId))
})

const onFilterCountryChange = () => {
  if (filterForm.cityId && !filteredCityOptions.value.find(c => c.id === filterForm.cityId)) {
    filterForm.cityId = null
  }
}

const onCountryChange = () => {
  if (form.cityId && !dialogCityOptions.value.find(c => c.id === form.cityId)) {
    form.cityId = null
  }
}

const statusTag = (code) => {
  const map = { 0: 'info', 1: 'success', 2: 'warning', 3: '', 4: 'danger' }
  return map[code] || 'info'
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)

const initForm = () => ({
  countryId: null, cityId: null, lineName: '', lineNo: '',
  lineColor: '', lineColorCn: '', totalKm: 0, stationCount: 0,
  transferLineCount: 0, transferLines: '', transferStationCount: 0,
  transferStations: '', trainCount: 0, avgSpeed: null,
  firstTime: '', lastTime: '', fullTime: null, openDate: '',
  statusCode: 0, extra: '',
})

const form = reactive(initForm())

const formRules = {
  countryId: [{ required: true, message: '请选择国家', trigger: 'change' }],
  cityId: [{ required: true, message: '请选择城市', trigger: 'change' }],
  lineName: [
    { required: true, message: '请输入线路名称', trigger: 'blur' },
    { max: 100, message: '最长100字符', trigger: 'blur' },
  ],
  lineNo: [
    { required: true, message: '请输入线路编号', trigger: 'blur' },
    { max: 20, message: '最长20字符', trigger: 'blur' },
  ],
}

const fetchCountries = async () => {
  try { const res = await getCountryAll(); countryOptions.value = normalizeList(res.data || [], ['id']) }
  catch (e) { console.error(e) }
}

const fetchCities = async () => {
  try { const res = await getCityAll(); cityOptions.value = normalizeList(res.data || [], ['id', 'countryId']) }
  catch (e) { console.error(e) }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getMetroLineList({
      keyword: filterForm.keyword, countryId: filterForm.countryId,
      cityId: filterForm.cityId, statusCode: filterForm.statusCode,
      minTotalKm: filterForm.minTotalKm, maxTotalKm: filterForm.maxTotalKm,
      minStationCount: filterForm.minStationCount, maxStationCount: filterForm.maxStationCount,
      minTrainCount: filterForm.minTrainCount, maxTrainCount: filterForm.maxTrainCount,
      sortField: sortField.value, sortOrder: sortOrder.value,
      pageNum: pageNum.value, pageSize: pageSize.value,
    })
    tableData.value = res.data.records || []
    total.value = Number(res.data.total) || 0
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const handleSearch = () => { pageNum.value = 1; fetchData() }
const handleCurrentChange = (val) => { pageNum.value = val; fetchData() }
const handleSizeChange = (val) => { pageSize.value = val; pageNum.value = 1; fetchData() }

const handleReset = () => {
  Object.assign(filterForm, {
    keyword: '', countryId: null, cityId: null, statusCode: null,
    minTotalKm: null, maxTotalKm: null,
    minStationCount: null, maxStationCount: null,
    minTrainCount: null, maxTrainCount: null,
  })
  showMoreFilters.value = false; handleSearch()
}

const handleTableSort = ({ prop, order }) => {
  if (prop && order) {
    sortField.value = prop; sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  } else { sortField.value = 'createdAt'; sortOrder.value = 'desc' }
  fetchData()
}

const handleSelectionChange = (rows) => { selectedIds.value = rows.map(r => r.id) }

const openCreateDialog = () => {
  isEdit.value = false; editingId.value = null
  Object.assign(form, initForm()); dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true; editingId.value = row.id
  form.countryId = ensureString(row.countryId); form.cityId = ensureString(row.cityId)
  form.lineName = row.lineName; form.lineNo = row.lineNo
  form.lineColor = row.lineColor || ''; form.lineColorCn = row.lineColorCn || ''
  form.totalKm = row.totalKm; form.stationCount = row.stationCount
  form.transferLineCount = row.transferLineCount; form.transferLines = row.transferLines || ''
  form.transferStationCount = row.transferStationCount; form.transferStations = row.transferStations || ''
  form.trainCount = row.trainCount; form.avgSpeed = row.avgSpeed
  form.firstTime = row.firstTime || ''; form.lastTime = row.lastTime || ''
  form.fullTime = row.fullTime; form.openDate = row.openDate || ''
  form.statusCode = row.statusCode; form.extra = row.extra || ''
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = {
      countryId: form.countryId, cityId: form.cityId,
      lineName: form.lineName, lineNo: form.lineNo,
      lineColor: form.lineColor || undefined,
      lineColorCn: form.lineColorCn || undefined,
      totalKm: form.totalKm, stationCount: form.stationCount,
      transferLineCount: form.transferLineCount,
      transferLines: form.transferLines || undefined,
      transferStationCount: form.transferStationCount,
      transferStations: form.transferStations || undefined,
      trainCount: form.trainCount, avgSpeed: form.avgSpeed,
      firstTime: form.firstTime || undefined,
      lastTime: form.lastTime || undefined,
      fullTime: form.fullTime, openDate: form.openDate || undefined,
      statusCode: form.statusCode, extra: form.extra || undefined,
    }
    if (isEdit.value) {
      if (!canEditLineName.value) { delete payload.lineName; delete payload.lineNo }
      await updateMetroLine(editingId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await createMetroLine(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false; fetchData()
  } catch (e) { /* handled */ }
  finally { submitting.value = false }
}

const handleDelete = async (id) => {
  try { await deleteMetroLine(id); ElMessage.success('删除成功'); fetchData() }
  catch (e) { /* handled */ }
}

const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedIds.value.length} 条线路吗？`, '批量删除确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
    await batchDeleteMetroLines(selectedIds.value)
    ElMessage.success('批量删除成功'); selectedIds.value = []; fetchData()
  } catch (e) { /* cancelled */ }
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
    const res = await batchImportMetroLines(importFile.value)
    ElMessage.success(`导入完成：成功 ${res.data.successCount} 条，共 ${res.data.totalCount} 条`)
    importDialogVisible.value = false; importFile.value = null; fetchData()
  } catch (e) { /* handled */ }
  finally { importing.value = false }
}

const exportSelected = () => {
  if (selectedIds.value.length === 0) return
  downloadExcel('/manage/metro-line/export', { ids: selectedIds.value.join(',') }, 'metro-lines.xlsx')
    .catch(() => ElMessage.error('导出失败'))
}

const exportAll = () => {
  const params = {}
  if (filterForm.keyword) params.keyword = filterForm.keyword
  if (filterForm.countryId) params.countryId = filterForm.countryId
  if (filterForm.cityId) params.cityId = filterForm.cityId
  if (filterForm.statusCode) params.statusCode = filterForm.statusCode
  downloadExcel('/manage/metro-line/export', params, 'metro-lines.xlsx')
    .catch(() => ElMessage.error('导出失败'))
}

onMounted(() => { fetchCountries(); fetchCities(); fetchData() })

watch(() => config.defaultPageSize, (val) => {
  if (!pageSizeInited.value && val !== pageSize.value) {
    pageSize.value = val
    fetchData()
  }
  pageSizeInited.value = true
})
</script>

<style scoped>
.metro-line-manage { padding: 0; }

.filter-card { margin-bottom: 16px; }
.filter-card :deep(.el-card__body) { padding: 16px 20px 0; }
.filter-form { margin-bottom: 0; }
.filter-form .el-form-item { margin-bottom: 12px; }

.range-sep { margin: 0 6px; color: #909399; font-size: 13px; }

.action-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.result-tip { font-size: 14px; color: #606266; }
.result-tip strong { color: #409EFF; }
.action-right { display: flex; gap: 8px; }

.table-card :deep(.el-card__body) { padding: 0; }

.line-badge { display: flex; align-items: center; gap: 8px; }
.line-badge-link { text-decoration: none; }
.line-badge-link:hover .line-badge-text { color: #409EFF; }
.line-color-dot { width: 14px; height: 14px; border-radius: 4px; flex-shrink: 0; }
.line-badge-text { font-weight: 600; color: #303133; transition: color 0.2s; }
.line-name-link { font-weight: 600; color: #303133; text-decoration: none; transition: color 0.2s; }
.line-name-link:hover { color: #409EFF; text-decoration: underline; }

.color-hex-text { margin-left: 8px; font-size: 13px; color: #909399; font-family: monospace; }

.pagination {
  margin-top: 16px; padding: 16px 20px; display: flex; justify-content: flex-end;
  background: #fff; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
</style>

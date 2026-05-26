<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="showResult ? '680px' : '520px'"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <!-- 上传模式 -->
    <template v-if="!showResult">
      <el-alert title="支持 JSON (.json) 和 Excel (.xls/.xlsx) 格式" type="info" :closable="false" show-icon style="margin-bottom: 12px" />
      <el-alert v-if="excelHeaders" :title="excelHeaders" type="warning" :closable="false" style="margin-bottom: 16px; font-size: 12px" />
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
    </template>

    <!-- 导入结果模式 -->
    <template v-else>
      <el-result icon="success" title="导入完成" :sub-title="resultSummary">
        <template #extra>
          <el-tag :type="result.successCount === result.totalCount ? 'success' : 'warning'" size="large">
            成功 {{ result.successCount }} / 共 {{ result.totalCount }}
          </el-tag>
        </template>
      </el-result>

      <el-divider v-if="result.errors && result.errors.length > 0" />

      <div v-if="result.errors && result.errors.length > 0">
        <h4 style="color: #e6a23c; margin: 0 0 8px 0;">
          以下 {{ result.errors.length }} 条记录导入失败：
        </h4>
        <el-table :data="result.errors" max-height="300" size="small" border stripe>
          <el-table-column type="index" label="序号" width="55" />
          <el-table-column prop="row" label="Excel行" width="70" />
          <el-table-column prop="stationName" label="站名" min-width="130" show-overflow-tooltip />
          <el-table-column label="失败原因" min-width="240">
            <template #default="{ row }">
              <div v-for="(reason, idx) in (row.reasons || [row.reason])" :key="idx">
                <el-tag type="danger" size="small" style="margin: 1px 0">✗</el-tag>
                {{ reason }}
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <template #footer>
      <el-button v-if="!showResult" @click="handleCancel">取消</el-button>
      <el-button v-if="!showResult" type="primary" :loading="importing" :disabled="!importFile" @click="handleImport">
        开始导入
      </el-button>
      <el-button v-else type="primary" @click="handleCloseResult">
        关闭
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'

const props = defineProps({
  title: { type: String, default: '批量导入' },
  excelHeaders: { type: String, default: '' },
  importFn: { type: Function, required: true },
})

const emit = defineEmits(['success'])

const visible = ref(false)
const importFile = ref(null)
const importing = ref(false)
const uploadRef = ref(null)
const showResult = ref(false)
const result = ref({ successCount: 0, totalCount: 0, errors: [] })
const resultSummary = ref('')

const open = () => {
  importFile.value = null
  showResult.value = false
  result.value = { successCount: 0, totalCount: 0, errors: [] }
  visible.value = true
}

const handleFileChange = (file) => { importFile.value = file.raw }
const handleFileRemove = () => { importFile.value = null }

const handleCancel = () => {
  visible.value = false
}

const handleCloseResult = () => {
  visible.value = false
  emit('success')
}

const handleImport = async () => {
  if (!importFile.value) return
  importing.value = true
  try {
    const res = await props.importFn(importFile.value)
    if (res && res.data) {
      const { successCount, totalCount, errors } = res.data
      result.value = {
        successCount: successCount || 0,
        totalCount: totalCount || 0,
        errors: errors || [],
      }
      resultSummary.value = errors && errors.length > 0
        ? `成功 ${successCount} 条，失败 ${(errors || []).length} 条`
        : `全部 ${totalCount} 条记录导入成功`
      showResult.value = true
    }
  } catch (e) {
    // handled by interceptor
  } finally {
    importing.value = false
  }
}

defineExpose({ open })
</script>

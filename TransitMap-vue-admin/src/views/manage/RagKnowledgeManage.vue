<template>
  <div class="rag-page">
    <div class="page-header">
      <h2><el-icon><Collection /></el-icon> RAG 知识库</h2>
      <div class="header-actions">
        <el-tag v-if="embStatus" :type="embStatus.missing > 0 ? 'warning' : 'success'" effect="plain">
          向量化 {{ embStatus.embedded }}/{{ embStatus.total }}
          <span v-if="embStatus.missing > 0">（待补 {{ embStatus.missing }}）</span>
        </el-tag>
        <el-dropdown @command="onEmbeddingCmd" v-if="userStore.roleCode >= 3">
          <el-button :icon="MagicStick" plain :loading="embedding">
            向量管理
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="backfill" :disabled="!embStatus || embStatus.missing === 0">
                补齐缺失向量（{{ embStatus?.missing || 0 }} 条）
              </el-dropdown-item>
              <el-dropdown-item command="rebuild" divided>
                强制重建全部向量
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button type="primary" :icon="Plus" @click="openCreate" v-if="userStore.roleCode >= 3">新增条目</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      Agent 会在用户提问时按关键词召回 TOP-K 条知识，注入给 LLM 作为参考。
      启用与召回数量请到「Agent 配置 → RAG」调整。
    </el-alert>

    <el-card>
      <div class="toolbar">
        <el-input v-model="filter.kw" placeholder="搜索标题/关键词/正文" clearable style="width: 240px" @keyup.enter="reload" />
        <el-select v-model="filter.category" placeholder="类别" clearable style="width: 140px" @change="reload">
          <el-option label="出口指引 exit" value="exit" />
          <el-option label="换乘 transfer" value="transfer" />
          <el-option label="设施 facility" value="facility" />
          <el-option label="常见问答 faq" value="faq" />
          <el-option label="临时通知 notice" value="notice" />
        </el-select>
        <el-input-number v-model.number="filter.cityId" placeholder="城市ID" :min="0" :controls="false" style="width: 140px" @change="reload" />
        <el-input-number v-model.number="filter.stationId" placeholder="站点ID" :min="0" :controls="false" style="width: 140px" @change="reload" />
        <el-button :icon="Refresh" plain @click="reload">刷新</el-button>
      </div>

      <el-table :data="rows" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="category" label="类别" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.category || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="范围" width="180">
          <template #default="{ row }">
            <span v-if="row.stationId">站点 {{ row.stationId }}</span>
            <span v-else-if="row.lineId">线路 {{ row.lineId }}</span>
            <span v-else-if="row.cityId">城市 {{ row.cityId }}</span>
            <span v-else style="color:#909399">全局</span>
          </template>
        </el-table-column>
        <el-table-column prop="keywords" label="关键词" min-width="200" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="向量" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.embedding" type="success" size="small">已向量化</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">无</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="160">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)" :disabled="userStore.roleCode < 3">编辑</el-button>
            <el-button size="small" text type="danger" @click="onDelete(row)" :disabled="userStore.roleCode < 3">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="reload"
        @size-change="reload"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <!-- 编辑 dialog -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑知识条目' : '新增知识条目'" width="720px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="例如：北京西站 → 公交换乘" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input v-model="form.content" type="textarea" :rows="8" placeholder="Markdown 格式" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="form.keywords" placeholder="逗号分隔，例如：北京西站,公交,换乘,出口" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category" placeholder="可选" clearable>
            <el-option label="出口指引 exit" value="exit" />
            <el-option label="换乘 transfer" value="transfer" />
            <el-option label="设施 facility" value="facility" />
            <el-option label="常见问答 faq" value="faq" />
            <el-option label="临时通知 notice" value="notice" />
          </el-select>
        </el-form-item>
        <el-form-item label="范围">
          <el-row :gutter="8" style="width: 100%">
            <el-col :span="8">
              <el-input-number v-model.number="form.cityId" placeholder="城市ID" :min="0" :controls="false" style="width: 100%" />
            </el-col>
            <el-col :span="8">
              <el-input-number v-model.number="form.lineId" placeholder="线路ID" :min="0" :controls="false" style="width: 100%" />
            </el-col>
            <el-col :span="8">
              <el-input-number v-model.number="form.stationId" placeholder="站点ID" :min="0" :controls="false" style="width: 100%" />
            </el-col>
          </el-row>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model.number="form.priority" :min="0" :max="100" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">数字越大越靠前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Collection, Plus, Refresh, MagicStick, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  listKnowledge, createKnowledge, updateKnowledge, deleteKnowledge,
  backfillEmbedding, rebuildEmbedding, getEmbeddingStatus
} from '@/api/ragKnowledge'

const userStore = useUserStore()
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const filter = reactive({ kw: '', category: '', cityId: null, stationId: null })

const formVisible = ref(false)
const submitting = ref(false)
const form = reactive(blankForm())

const embStatus = ref(null)
const embedding = ref(false)

function blankForm() {
  return {
    id: null, title: '', content: '', keywords: '',
    category: '', cityId: null, lineId: null, stationId: null,
    priority: 0, status: 1
  }
}

async function reload() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (filter.kw) params.kw = filter.kw
    if (filter.category) params.category = filter.category
    if (filter.cityId) params.cityId = filter.cityId
    if (filter.stationId) params.stationId = filter.stationId
    const res = await listKnowledge(params)
    if (res.code === 200) {
      rows.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } finally {
    loading.value = false
  }
  refreshEmbStatus()
}

async function refreshEmbStatus() {
  try {
    const res = await getEmbeddingStatus()
    if (res.code === 200) embStatus.value = res.data
  } catch {}
}

async function onEmbeddingCmd(cmd) {
  if (cmd === 'backfill') {
    embedding.value = true
    try {
      const res = await backfillEmbedding(100)
      if (res.code === 200) {
        ElMessage.success(`已补齐 ${res.data.ok} 条向量`)
        await reload()
      }
    } catch {}
    finally { embedding.value = false }
  } else if (cmd === 'rebuild') {
    try {
      await ElMessageBox.confirm(
        '将清空所有知识条目的旧向量并重新计算，可能消耗较多 Embedding API 配额。确定继续？',
        '强制重建向量', { confirmButtonText: '确定重建', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { return }
    embedding.value = true
    try {
      const res = await rebuildEmbedding()
      if (res.code === 200) {
        ElMessage.success(`已重建 ${res.data.total} 条向量`)
        await reload()
      }
    } catch {}
    finally { embedding.value = false }
  }
}

function openCreate() {
  Object.assign(form, blankForm())
  formVisible.value = true
}
function openEdit(row) {
  Object.assign(form, row)
  formVisible.value = true
}

async function submitForm() {
  if (!form.title?.trim()) return ElMessage.warning('标题不能为空')
  if (!form.content?.trim()) return ElMessage.warning('正文不能为空')
  submitting.value = true
  try {
    if (form.id) {
      await updateKnowledge(form.id, form)
      ElMessage.success('已更新')
    } else {
      await createKnowledge(form)
      ElMessage.success('已创建')
    }
    formVisible.value = false
    reload()
  } finally {
    submitting.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.title}」？`, '提示', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    await deleteKnowledge(row.id)
    ElMessage.success('已删除')
    reload()
  } catch {}
}

function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(reload)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
</style>

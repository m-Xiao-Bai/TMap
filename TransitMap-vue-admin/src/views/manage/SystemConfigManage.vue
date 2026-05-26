<template>
  <div class="manage-page">
    <div class="page-header">
      <h2><el-icon><Setting /></el-icon> 系统配置管理</h2>
      <div>
        <el-button type="primary" :icon="Check" :loading="saving" @click="handleSave">保存配置</el-button>
      </div>
    </div>

    <el-alert
      title="此处可修改系统业务配置项，修改后会影响所有用户看到的内容。请谨慎操作。"
      type="warning" show-icon :closable="false" style="margin-bottom:16px"
    />

    <div v-loading="loading">
      <el-card v-for="group in configGroups" :key="group.name" class="config-card" shadow="hover">
        <template #header>
          <div class="card-hd">
            <el-icon><component :is="group.icon" /></el-icon>
            <span>{{ group.label }}</span>
          </div>
        </template>

        <!-- 普通配置组：站点、分页、认证、缓存 -->
        <template v-if="group.name !== 'map' && group.name !== 'ticket'">
          <el-form label-width="140px" label-position="top">
            <div v-for="cfg in group.items" :key="cfg.configKey" class="config-item">
              <div class="config-label">
                <span class="label-text">{{ cfg.description || cfg.configKey }}</span>
                <span class="label-key">{{ cfg.configKey }}</span>
              </div>

              <div v-if="cfg.configType === 'json'" class="config-value">
                <el-input
                  v-model="cfg.configValue"
                  type="textarea"
                  :rows="cfg.configKey === 'station.status_map' || cfg.configKey === 'station.type_map' ? 8 : 4"
                  placeholder="请输入JSON格式的值"
                />
                <div class="json-preview" v-if="tryParseJson(cfg.configValue)">
                  <span class="preview-label">预览：</span>
                  <template v-if="isMapConfig(cfg.configKey)">
                    <el-tag v-for="(v, k) in tryParseJson(cfg.configValue)" :key="k" size="small" style="margin:2px">
                      {{ k }} → {{ v }}
                    </el-tag>
                  </template>
                  <template v-else>
                    <code>{{ JSON.stringify(tryParseJson(cfg.configValue)) }}</code>
                  </template>
                </div>
                <div v-else class="json-error">
                  <span style="color:#F56C6C;font-size:12px">JSON格式无效</span>
                </div>
              </div>

              <div v-else-if="cfg.configType === 'number'" class="config-value">
                <el-input-number
                  :model-value="Number(cfg.configValue) || 0"
                  @update:model-value="v => cfg.configValue = v != null ? String(v) : '0'"
                  :min="0"
                  :step="cfg.configKey.startsWith('cache.ttl') ? 3600 : 1"
                  :step-strictly="false"
                  style="width:240px"
                />
                <span v-if="cfg.configKey.startsWith('cache.ttl')" class="cache-hint">
                  {{ formatCacheTTL(cfg.configValue) }}
                </span>
              </div>
              <div v-else-if="cfg.configType === 'secret'" class="config-value">
                <el-input
                  v-model="cfg.configValue"
                  show-password
                  placeholder="留空表示不修改；填入明文将自动加密存储"
                  style="max-width: 480px"
                />
                <el-button
                  v-if="cfg.configKey === 'agent.llm.api_key'"
                  link
                  type="primary"
                  size="small"
                  style="margin-left: 8px"
                  @click="testLlmConnectivity"
                >测试连通性</el-button>
                <div class="secret-hint">
                  当前为加密存储，前端不显示明文。提交空值不会覆盖原值。
                </div>
              </div>
              <div v-else class="config-value">
                <el-input v-model="cfg.configValue" :placeholder="cfg.description" />
              </div>
            </div>
          </el-form>
        </template>

        <!-- 地图配置：逐项 key-value 展示 -->
        <template v-else-if="group.name === 'map'">
          <div v-for="section in mapFieldSections" :key="section.configKey" class="kv-section">
            <div class="kv-section-title">
              <span>{{ section.title }}</span>
              <span class="label-key">{{ section.configKey }}</span>
            </div>
            <p class="kv-section-desc">{{ section.desc }}</p>
            <div class="kv-grid">
              <div v-for="field in section.fields" :key="field.key" class="kv-row">
                <div class="kv-label">
                  <span class="kv-label-text">{{ field.label }}</span>
                  <span class="kv-label-hint">{{ field.hint }}</span>
                </div>
                <div class="kv-value">
                  <el-color-picker
                    v-if="field.type === 'color'"
                    v-model="mapEdits[field.key]"
                  />
                  <el-input-number
                    v-else-if="field.type === 'number'"
                    v-model="mapEdits[field.key]"
                    :min="field.min ?? 0"
                    :max="field.max ?? 999"
                    :step="field.step ?? 1"
                    :precision="field.precision ?? 0"
                    style="width:160px"
                  />
                  <el-input
                    v-else
                    v-model="mapEdits[field.key]"
                    style="width:200px"
                  />
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 票务配置：逐项 key-value 展示 + 票价阶梯表格 -->
        <template v-else-if="group.name === 'ticket'">
          <div v-for="section in ticketFieldSections" :key="section.configKey" class="kv-section">
            <div class="kv-section-title">
              <span>{{ section.title }}</span>
              <span class="label-key">{{ section.configKey }}</span>
            </div>
            <p class="kv-section-desc">{{ section.desc }}</p>

            <!-- 普通 key-value 字段 -->
            <div v-if="section.fields" class="kv-grid">
              <div v-for="field in section.fields" :key="field.key" class="kv-row">
                <div class="kv-label">
                  <span class="kv-label-text">{{ field.label }}</span>
                  <span class="kv-label-hint">{{ field.hint }}</span>
                </div>
                <div class="kv-value">
                  <el-input-number
                    v-model="ticketEdits[field.key]"
                    :min="field.min ?? 0"
                    :max="field.max ?? 999"
                    :step="field.step ?? 1"
                    :precision="field.precision ?? 1"
                    style="width:160px"
                  />
                  <span class="kv-unit">{{ field.unit }}</span>
                </div>
              </div>
            </div>

            <!-- 票价阶梯表格 -->
            <div v-if="section.isArray" class="tier-table-wrap">
              <el-table :data="priceTiers" border size="small" class="tier-table">
                <el-table-column label="站数范围" min-width="180">
                  <template #default="{ row, $index }">
                    <span v-if="$index === 0">≤</span>
                    <span v-else>{{ priceTiers[$index - 1].maxStops + 1 }} ~</span>
                    <el-input-number
                      v-model="row.maxStops"
                      :min="1" :max="9999" :step="1"
                      size="small"
                      style="width:100px;margin:0 6px"
                    />
                    <span>站</span>
                  </template>
                </el-table-column>
                <el-table-column label="票价（元）" width="160">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.price"
                      :min="1" :max="99" :step="1"
                      size="small"
                      style="width:100px"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center">
                  <template #default="{ $index }">
                    <el-button
                      type="danger" text size="small"
                      :disabled="priceTiers.length <= 1"
                      @click="priceTiers.splice($index, 1)"
                    >删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button
                size="small" style="margin-top:8px"
                @click="priceTiers.push({ maxStops: priceTiers[priceTiers.length-1].maxStops + 3, price: priceTiers[priceTiers.length-1].price + 1 })"
              >添加阶梯</el-button>
            </div>
          </div>
        </template>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting, Check, Tickets, Odometer, Lock, Coin, MapLocation, Ticket } from '@element-plus/icons-vue'
import { getAllConfigs, updateConfigs } from '@/api/systemConfig'

const loading = ref(false)
const saving = ref(false)
const rawConfigs = ref([])

// ===== 地图配置字段元数据 =====
const mapFieldSections = [
  {
    configKey: 'map.route_style', title: '路线高亮样式', desc: '用户查询路线后，高亮轨迹的视觉效果参数',
    fields: [
      { key: 'routeColor',       label: '路线颜色',     hint: '高亮轨迹主色',     type: 'color' },
      { key: 'lineWeight',       label: '路线宽度',     hint: '主线粗细（像素）', type: 'number', min: 1, max: 20, step: 1 },
      { key: 'lineOpacity',      label: '路线透明度',   hint: '0~1，1为完全不透明', type: 'number', min: 0, max: 1, step: 0.05, precision: 2 },
      { key: 'glowWeight',       label: '光晕宽度',     hint: '路线外围发光粗细', type: 'number', min: 0, max: 40, step: 1 },
      { key: 'glowOpacity',      label: '光晕透明度',   hint: '发光效果透明度',   type: 'number', min: 0, max: 1, step: 0.05, precision: 2 },
      { key: 'dashArray',        label: '虚线样式',     hint: '如 "12 6" 表示虚线', type: 'text' },
      { key: 'endpointRadius',   label: '端点半径',     hint: '起点/终点圆圈大小', type: 'number', min: 1, max: 30, step: 1 },
      { key: 'endpointWeight',   label: '端点边框',     hint: '起点/终点边框粗细', type: 'number', min: 1, max: 10, step: 1 },
      { key: 'midpointRadius',   label: '换乘点半径',   hint: '中间换乘站圆圈大小', type: 'number', min: 1, max: 20, step: 1 },
      { key: 'midpointWeight',   label: '换乘点边框',   hint: '换乘站边框粗细',   type: 'number', min: 1, max: 10, step: 1 },
      { key: 'dimLineGlowOpacity', label: '非高亮光晕透明度', hint: '未选中路线的光晕透明度', type: 'number', min: 0, max: 1, step: 0.01, precision: 2 },
      { key: 'dimLineOpacity',   label: '非高亮路线透明度', hint: '未选中路线的主线透明度', type: 'number', min: 0, max: 1, step: 0.01, precision: 2 },
      { key: 'dimStationOpacity', label: '非高亮站点透明度', hint: '未选中站点的透明度',   type: 'number', min: 0, max: 1, step: 0.01, precision: 2 },
      { key: 'dimStationRadius', label: '非高亮站点半径', hint: '未选中站点的圆点大小',   type: 'number', min: 1, max: 10, step: 1 },
    ],
  },
  {
    configKey: 'map.label_config', title: '站点标签缩放', desc: '缩放地图时，站点名称标签的显示行为',
    fields: [
      { key: 'baseFontSize',     label: '基础字号',     hint: '正常缩放级别下的字号（px）', type: 'number', min: 6, max: 24, step: 1 },
      { key: 'minFontSize',      label: '最小字号',     hint: '缩到最小时的字号（px）',     type: 'number', min: 1, max: 12, step: 1 },
      { key: 'shrinkStartZoom',  label: '缩小起始层级', hint: '低于此zoom开始缩小标签',     type: 'number', min: 5, max: 20, step: 1 },
      { key: 'hideZoom',         label: '隐藏层级',     hint: '低于此zoom标签完全隐藏',     type: 'number', min: 1, max: 18, step: 1 },
      { key: 'fontWeight',       label: '字重',         hint: '400=正常 500=中等 600=粗体', type: 'number', min: 300, max: 800, step: 100 },
      { key: 'color',            label: '文字颜色',     hint: '标签文字颜色',               type: 'color' },
    ],
  },
]

// ===== 票务配置字段元数据 =====
const ticketFieldSections = [
  {
    configKey: 'ticket.price_tiers', title: '票价阶梯', desc: '根据乘坐站数区间计算票价，从上到下匹配第一个满足条件的阶梯',
    isArray: true,
  },
  {
    configKey: 'ticket.estimate_params', title: '行程估算参数', desc: '乘车时间按「每站耗时」估算；距离优先使用数据库中相邻站的实际距离（双向取平均），仅在无实际距离数据时使用「每站距离」作为回退值',
    fields: [
      { key: 'minutesPerStop', label: '每站耗时', hint: '经过每个站约需时间', unit: '分钟', min: 1, max: 10, step: 0.5, precision: 1 },
      { key: 'minMinutes',     label: '最低耗时', hint: '路线至少需要的时间', unit: '分钟', min: 1, max: 30, step: 1, precision: 0 },
      { key: 'kmPerStop',      label: '每站距离', hint: '无实际距离数据时的回退值', unit: '公里', min: 0.5, max: 10, step: 0.1, precision: 1 },
    ],
  },
]

// ===== 地图配置编辑状态 =====
const mapEdits = reactive({})
// ===== 票务配置编辑状态 =====
const ticketEdits = reactive({})
const priceTiers = ref([])

const tryParseJson = (val) => {
  try { return JSON.parse(val) } catch { return null }
}

const isMapConfig = (key) => key === 'station.status_map' || key === 'station.type_map'

const formatCacheTTL = (seconds) => {
  const s = Number(seconds) || 0
  if (s <= 0) return '已禁用'
  if (s < 60) return `${s}秒`
  if (s < 3600) return `${Math.round(s / 60)}分钟`
  if (s < 86400) return `${Math.round(s / 3600)}小时`
  return `${Math.round(s / 86400)}天`
}

const configGroups = computed(() => {
  const groups = [
    {
      name: 'station', label: '站点配置', icon: Tickets,
      items: rawConfigs.value.filter(c => c.configGroup === 'station'),
    },
    {
      name: 'pagination', label: '分页配置', icon: Odometer,
      items: rawConfigs.value.filter(c => c.configGroup === 'pagination'),
    },
    {
      name: 'auth', label: '认证配置', icon: Lock,
      items: rawConfigs.value.filter(c => c.configGroup === 'auth'),
    },
    {
      name: 'cache', label: '缓存配置', icon: Coin,
      items: rawConfigs.value.filter(c => c.configGroup === 'cache'),
    },
    {
      name: 'map', label: '地图配置', icon: MapLocation,
      items: rawConfigs.value.filter(c => c.configGroup === 'map'),
    },
    {
      name: 'ticket', label: '票务配置', icon: Ticket,
      items: rawConfigs.value.filter(c => c.configGroup === 'ticket'),
    },
  ]
  return groups.filter(g => g.items.length > 0)
})

const initEdits = () => {
  // 解析 map 配置到 mapEdits
  const routeStyleItem = rawConfigs.value.find(c => c.configKey === 'map.route_style')
  const routeStyle = tryParseJson(routeStyleItem?.configValue) || {}
  const allMapFields = mapFieldSections.flatMap(s => s.fields)
  for (const f of allMapFields) {
    mapEdits[f.key] = routeStyle[f.key] ?? ''
  }

  const labelItem = rawConfigs.value.find(c => c.configKey === 'map.label_config')
  const labelCfg = tryParseJson(labelItem?.configValue) || {}
  for (const f of mapFieldSections[1].fields) {
    if (mapEdits[f.key] === undefined || mapEdits[f.key] === '') {
      mapEdits[f.key] = labelCfg[f.key] ?? ''
    }
  }

  // 解析 ticket 配置到 ticketEdits / priceTiers
  const estimateItem = rawConfigs.value.find(c => c.configKey === 'ticket.estimate_params')
  const estimate = tryParseJson(estimateItem?.configValue) || {}
  for (const f of ticketFieldSections[1].fields) {
    ticketEdits[f.key] = estimate[f.key] ?? 0
  }

  const tiersItem = rawConfigs.value.find(c => c.configKey === 'ticket.price_tiers')
  const tiers = tryParseJson(tiersItem?.configValue) || []
  priceTiers.value = tiers.map(t => ({ maxStops: t.maxStops, price: t.price }))
}

const fetchConfigs = async () => {
  loading.value = true
  try {
    const res = await getAllConfigs()
    const grouped = res.data || {}
    const flat = []
    Object.entries(grouped).forEach(([group, items]) => {
      items.forEach(item => flat.push({ ...item, configGroup: group }))
    })
    rawConfigs.value = flat
    initEdits()
  } catch {
    ElMessage.error('获取配置失败')
  } finally { loading.value = false }
}

const handleSave = async () => {
  saving.value = true
  try {
    const payload = rawConfigs.value
      .filter(c => {
        // secret 字段空值不提交（保留原值）
        if (c.configType === 'secret' && (!c.configValue || !String(c.configValue).trim())) {
          return false
        }
        return true
      })
      .map(c => {
      // 将 map 的 key-value 编辑合并回 JSON
      if (c.configKey === 'map.route_style') {
        const obj = {}
        for (const f of mapFieldSections[0].fields) {
          const v = mapEdits[f.key]
          obj[f.key] = f.type === 'number' ? Number(v) : v
        }
        return { configKey: c.configKey, configValue: JSON.stringify(obj), description: c.description }
      }
      if (c.configKey === 'map.label_config') {
        const obj = {}
        for (const f of mapFieldSections[1].fields) {
          const v = mapEdits[f.key]
          obj[f.key] = f.type === 'number' ? Number(v) : v
        }
        return { configKey: c.configKey, configValue: JSON.stringify(obj), description: c.description }
      }
      // 将 ticket estimate_params 编辑合并回 JSON
      if (c.configKey === 'ticket.estimate_params') {
        const obj = {}
        for (const f of ticketFieldSections[1].fields) {
          obj[f.key] = Number(ticketEdits[f.key])
        }
        return { configKey: c.configKey, configValue: JSON.stringify(obj), description: c.description }
      }
      // 将 price_tiers 编辑合并回 JSON
      if (c.configKey === 'ticket.price_tiers') {
        const tiers = priceTiers.value.map(t => ({
          maxStops: Math.round(Number(t.maxStops)),
          price: Math.round(Number(t.price)),
        }))
        return { configKey: c.configKey, configValue: JSON.stringify(tiers), description: c.description }
      }
      return { configKey: c.configKey, configValue: String(c.configValue), description: c.description }
    })
    await updateConfigs(payload)
    ElMessage.success('配置已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally { saving.value = false }
}

const testLlmConnectivity = async () => {
  ElMessage.info('请保存配置后再测试。测试请求会真实调用一次 LLM API。')
  // 真正实现可参考：调用后端 /chat-manage/test-llm，返回成功/失败
}

onMounted(() => { fetchConfigs() })
</script>

<style scoped>
.manage-page { padding: 4px 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; display: flex; align-items: center; gap: 8px; }

.config-card { margin-bottom: 16px; border-radius: 12px; }
.card-hd { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 15px; }

.config-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}
.config-item:last-child { border-bottom: none; }
.config-label {
  margin-bottom: 8px;
  display: flex; align-items: center; gap: 8px;
}
.label-text { font-size: 14px; color: #303133; font-weight: 600; }
.label-key { font-size: 11px; color: #bbb; font-family: monospace; }
.config-value { width: 100%; }

.json-preview {
  margin-top: 8px; padding: 8px 12px;
  background: #f5f7fa; border-radius: 6px;
}
.secret-hint {
  margin-top: 6px;
  font-size: 11px;
  color: #909399;
}
.preview-label { font-size: 12px; color: #909399; }
.json-error { margin-top: 4px; }

.cache-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  padding: 2px 8px;
  border-radius: 4px;
}

/* ===== 地图/票务 key-value 样式 ===== */
.kv-section {
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}
.kv-section:last-child { border-bottom: none; }
.kv-section-title {
  font-size: 15px; font-weight: 600; color: #303133;
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 4px;
}
.kv-section-desc {
  font-size: 13px; color: #909399; margin: 0 0 16px 0;
}
.kv-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 14px 32px;
}
.kv-row {
  display: flex; align-items: center; gap: 12px;
}
.kv-label { flex-shrink: 0; width: 140px; }
.kv-label-text { font-size: 14px; color: #303133; font-weight: 500; }
.kv-label-hint { display: block; font-size: 11px; color: #c0c4cc; margin-top: 2px; }
.kv-value { display: flex; align-items: center; gap: 6px; }
.kv-unit { font-size: 12px; color: #909399; }

/* ===== 票价阶梯表格 ===== */
.tier-table-wrap { margin-top: 12px; }
.tier-table { width: 100%; }
</style>

<template>
  <div class="station-selector">
    <el-select
      :model-value="internalValue"
      :multiple="isTransfer === 1 || isTransfer === '1'"
      filterable
      clearable
      placeholder="搜索并选择站点"
      style="width:100%; margin-bottom:8px;"
      value-key="stationId"
      @change="onSelectChange"
    >
      <el-option
        v-for="s in availableOptions"
        :key="s.id"
        :label="optionLabel(s)"
        :value="buildOptionValue(s)"
      >
        <div class="station-option-item">
          <span class="station-option-name">{{ s.stationName }}</span>
          <span
            v-for="(ln, li) in safeParse(s.lineNames)"
            :key="li"
            class="station-option-line"
          >
            <span class="line-dot" :style="{ background: getLineColor(ln) || '#409EFF' }"></span>
            {{ ln }}
          </span>
        </div>
      </el-option>
    </el-select>

    <el-alert
      v-if="missingLatLngNames.length > 0"
      :title="'以下站点缺少经纬度，无法计算距离：' + missingLatLngNames.join('、')"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom:8px; font-size:12px;"
    />

    <div v-if="internalValue.length > 0" class="selected-list">
      <div v-for="(item, idx) in internalValue" :key="item.stationId" class="selected-item">
        <el-tag
          closable
          :type="item.distance == null || item.distance === '' || Number(item.distance) < 0 ? 'danger' : 'info'"
          @close="removeItem(idx)"
        >
          {{ item.stationName }}
          <span v-if="item.lineName" class="tag-line">({{ item.lineName }})</span>
        </el-tag>
        <div class="distance-input-group">
          <span class="distance-label">距离：</span>
          <el-input
            :model-value="item.distance"
            size="small"
            class="distance-input"
            placeholder="km"
            @input="(v) => updateDistance(idx, v)"
          />
          <span class="distance-unit">km</span>
        </div>
      </div>
    </div>

    <div v-if="internalValue.length > 0" class="selector-actions">
      <el-button size="small" type="primary" link @click="$emit('recalculate')">
        重新计算距离
      </el-button>
      <el-button size="small" type="danger" link @click="clearAll">清除全部</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  stationOptions: { type: Array, default: () => [] },
  selected: { type: Array, default: () => [] },
  isTransfer: { type: [Number, String], default: 0 },
  lineColors: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['update:selected', 'recalculate'])

const internalValue = ref([])

watch(() => props.selected, (val) => {
  internalValue.value = JSON.parse(JSON.stringify(val || []))
}, { deep: true, immediate: true })

const selectedIds = computed(() => new Set(internalValue.value.map(s => s.stationId)))

const availableOptions = computed(() => {
  return props.stationOptions.filter(s => !selectedIds.value.has(s.id))
})

const missingLatLngNames = computed(() => {
  return internalValue.value
    .filter(item => {
      const station = props.stationOptions.find(s => s.id === item.stationId)
      return station && (!station.latitude && !station.longitude)
    })
    .map(item => item.stationName)
})

const safeParse = (val) => {
  if (!val) return []
  try { const arr = JSON.parse(val); return Array.isArray(arr) ? arr : [] }
  catch { return [] }
}

const getLineColor = (lineName) => {
  return props.lineColors[lineName] || null
}

const optionLabel = (station) => station.stationName

const buildOptionValue = (station) => {
  const lNames = safeParse(station.lineNames)
  const lIds = safeParse(station.lineIds)
  return {
    stationId: station.id,
    stationName: station.stationName,
    distance: '',
    lineId: lIds.length > 0 ? lIds[0] : null,
    lineName: lNames.length > 0 ? lNames[0] : null,
  }
}

const onSelectChange = (val) => {
  if (props.isTransfer === 0 || props.isTransfer === '0') {
    internalValue.value = val ? [val] : []
  } else {
    internalValue.value = val || []
  }
  emit('update:selected', [...internalValue.value])
}

const removeItem = (idx) => {
  internalValue.value.splice(idx, 1)
  emit('update:selected', [...internalValue.value])
}

const updateDistance = (idx, val) => {
  if (internalValue.value[idx]) {
    internalValue.value[idx].distance = val
    emit('update:selected', [...internalValue.value])
  }
}

const clearAll = () => {
  internalValue.value = []
  emit('update:selected', [])
}
</script>

<style scoped>
.station-selector { width: 100%; }
.station-option-item { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.station-option-name { font-weight: 500; }
.station-option-line { display: inline-flex; align-items: center; gap: 3px; font-size: 11px; color: #909399; white-space: nowrap; }
.line-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; }
.selected-list { display: flex; flex-direction: column; gap: 6px; margin-top: 4px; }
.selected-item { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.distance-input-group { display: flex; align-items: center; gap: 4px; }
.distance-label { font-size: 12px; color: #909399; white-space: nowrap; }
.distance-input { width: 90px; }
.distance-unit { font-size: 12px; color: #909399; }
.tag-line { font-size: 11px; opacity: 0.7; }
.selector-actions { display: flex; gap: 8px; margin-top: 8px; }
</style>

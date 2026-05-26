<template>
  <div v-if="visible && items.length" class="suggest-popover">
    <div
      v-for="(item, i) in items"
      :key="(item.type || 't') + '-' + (item.id ?? i)"
      class="suggest-item"
      :class="{ active: i === highlightIndex }"
      @mousedown.prevent="$emit('select', item)"
      @mouseenter="$emit('hover', i)"
    >
      <div class="item-main">
        <span class="item-icon">{{ item.type === 'city' ? '🏙️' : '🚉' }}</span>
        <span class="item-name">{{ item.name }}</span>
      </div>
      <span v-if="item.subtitle" class="item-subtitle">{{ item.subtitle }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  visible: Boolean,
  items: { type: Array, default: () => [] },
  highlightIndex: { type: Number, default: 0 }
})
defineEmits(['select', 'hover'])
</script>

<style scoped>
.suggest-popover {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 0;
  right: 0;
  max-height: 260px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e8eaed;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 50;
  padding: 4px;
}

.suggest-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.suggest-item:hover,
.suggest-item.active {
  background: #f0f7ff;
}

.item-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.item-icon {
  flex-shrink: 0;
  font-size: 14px;
}

.item-name {
  font-size: 13px;
  font-weight: 500;
  color: #1d1d1f;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-subtitle {
  font-size: 11px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 40%;
}
</style>

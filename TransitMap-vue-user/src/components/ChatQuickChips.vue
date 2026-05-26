<template>
  <div class="quick-chips">
    <button
      v-for="item in items"
      :key="item"
      class="chip"
      @click="$emit('pick', item)"
    >{{ item }}</button>
  </div>
</template>

<script setup>
defineProps({
  items: { type: Array, default: () => [] }
})

defineEmits(['pick'])
</script>

<style scoped>
.quick-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
}

.chip {
  padding: 8px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  background: #fff;
  font-size: 13px;
  color: #1a73e8;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.chip:hover {
  background: #f0f7ff;
  border-color: #1a73e8;
  transform: translateY(-1px);
}
</style>

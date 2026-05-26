<template>
  <div class="session-dropdown" @click.stop>
    <button class="trigger" @click="toggleOpen">
      <svg viewBox="0 0 24 24" width="14"><path d="M4 6h16v2H4zm0 5h16v2H4zm0 5h16v2H4z" fill="currentColor"/></svg>
      <span class="trigger-text">{{ currentTitle }}</span>
      <svg viewBox="0 0 24 24" width="12" :class="{ open: isOpen }" class="arrow">
        <path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z" fill="currentColor"/>
      </svg>
    </button>

    <div v-if="isOpen" class="dropdown">
      <div class="dropdown-header">
        <span class="header-title">最近会话</span>
        <button class="new-btn" @click="onNew">
          <svg viewBox="0 0 24 24" width="12"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" fill="currentColor"/></svg>
          新对话
        </button>
      </div>
      <div class="list">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="item"
          :class="{ active: s.id === currentSessionId }"
          @click="onSelect(s.id)"
        >
          <div class="item-title">{{ s.title || '新对话' }}</div>
          <div class="item-meta">
            <span>{{ s.msgCount || 0 }} 条</span>
            <span>·</span>
            <span>{{ formatTime(s.lastMsgAt || s.createTime) }}</span>
          </div>
          <button
            class="del-btn"
            title="删除会话"
            @click.stop="onDelete(s.id)"
          >
            <svg viewBox="0 0 24 24" width="12"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" fill="currentColor"/></svg>
          </button>
        </div>
        <div v-if="!sessions.length" class="empty">还没有任何会话</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { getSessionList, newSession as apiNew, deleteSession as apiDel } from '@/api/agent'

const props = defineProps({
  currentSessionId: { type: [Number, String], default: null }
})

const emit = defineEmits(['select', 'create'])

const isOpen = ref(false)
const sessions = ref([])

const currentTitle = computed(() => {
  const cur = sessions.value.find(s => s.id === props.currentSessionId)
  return cur?.title || '新对话'
})

async function loadSessions() {
  try {
    const res = await getSessionList()
    if (res.code === 200) {
      sessions.value = res.data || []
    }
  } catch (e) {
    console.warn('Failed to load sessions', e)
  }
}

function toggleOpen() {
  isOpen.value = !isOpen.value
  if (isOpen.value) loadSessions()
}

function close() {
  isOpen.value = false
}

function onSelect(id) {
  emit('select', id)
  close()
}

async function onNew() {
  try {
    const res = await apiNew()
    if (res.code === 200 && res.data) {
      emit('create', res.data.id)
      await loadSessions()
    }
  } catch (e) {
    console.warn('Failed to create session', e)
  }
  close()
}

async function onDelete(id) {
  try {
    await apiDel(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
    if (id === props.currentSessionId && sessions.value.length > 0) {
      emit('select', sessions.value[0].id)
    }
  } catch (e) {
    console.warn('Failed to delete session', e)
  }
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diffMs = now - d
  if (diffMs < 60_000) return '刚刚'
  if (diffMs < 3600_000) return Math.floor(diffMs / 60_000) + '分钟前'
  if (diffMs < 86400_000 && d.getDate() === now.getDate()) {
    return d.toTimeString().slice(0, 5)
  }
  if (diffMs < 86400_000 * 7) {
    return Math.floor(diffMs / 86400_000) + '天前'
  }
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function onDocClick(e) {
  if (!e.target.closest('.session-dropdown')) close()
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
})

defineExpose({ refresh: loadSessions })
</script>

<style scoped>
.session-dropdown {
  position: relative;
}

.trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  background: #fff;
  color: #5f6368;
  font-size: 12px;
  cursor: pointer;
  max-width: 180px;
  transition: all 0.15s;
}

.trigger:hover {
  border-color: #1a73e8;
  color: #1a73e8;
}

.trigger-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.arrow {
  flex-shrink: 0;
  transition: transform 0.15s;
}
.arrow.open { transform: rotate(180deg); }

.dropdown {
  position: absolute;
  left: 0;
  top: calc(100% + 6px);
  width: 280px;
  max-height: 380px;
  background: #fff;
  border: 1px solid #e8eaed;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 100;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dropdown-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.header-title {
  font-size: 12px;
  color: #909399;
  font-weight: 600;
}

.new-btn {
  display: flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: #f0f7ff;
  color: #1a73e8;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.new-btn:hover {
  background: #e3f0fd;
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}

.item {
  position: relative;
  padding: 8px 28px 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.item:hover {
  background: #f5f7fa;
}

.item.active {
  background: #e8f0fe;
}

.item-title {
  font-size: 13px;
  color: #1d1d1f;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item.active .item-title {
  color: #1a73e8;
  font-weight: 600;
}

.item-meta {
  display: flex;
  gap: 4px;
  margin-top: 2px;
  font-size: 11px;
  color: #909399;
}

.del-btn {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: #c0c4cc;
  border-radius: 50%;
  cursor: pointer;
  opacity: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.item:hover .del-btn {
  opacity: 1;
}

.del-btn:hover {
  background: #fef0f0;
  color: #ea4335;
}

.empty {
  padding: 24px 12px;
  text-align: center;
  color: #909399;
  font-size: 12px;
}
</style>

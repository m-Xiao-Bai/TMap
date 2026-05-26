<template>
  <div v-if="cfg" class="cfg-field">
    <div class="field-label">
      <span class="label-text">{{ cfg.description || cfg.configKey }}</span>
      <span class="label-key">{{ cfg.configKey }}</span>
    </div>

    <div class="field-value">
      <!-- secret -->
      <el-input
        v-if="cfg.configType === 'secret'"
        v-model="cfg.configValue"
        show-password
        :placeholder="secretPlaceholder"
        :class="{ 'has-decrypt-error': hasDecryptError }"
        clearable
      />
      <div v-if="hasDecryptError" class="decrypt-warn">
        ⚠ 当前密文用旧 master key 加密，解密失败。请重新填入明文后保存。
      </div>

      <!-- number -->
      <el-input-number
        v-else-if="cfg.configType === 'number'"
        :model-value="toNumber(cfg.configValue)"
        @update:model-value="v => cfg.configValue = v != null ? String(v) : '0'"
        :min="0"
        :step="1"
        style="width: 240px"
      />

      <!-- multiline string -->
      <el-input
        v-else-if="multiline"
        v-model="cfg.configValue"
        type="textarea"
        :rows="rows"
        :placeholder="placeholder"
      />

      <!-- json（保留原文本编辑入口，前提是父组件未单独处理） -->
      <el-input
        v-else-if="cfg.configType === 'json'"
        v-model="cfg.configValue"
        type="textarea"
        :rows="6"
        placeholder="请输入 JSON 字符串"
      />

      <!-- 普通字符串 -->
      <el-input
        v-else
        v-model="cfg.configValue"
        :placeholder="placeholder"
        clearable
      />
    </div>

    <div v-if="$slots.hint" class="field-hint">
      <slot name="hint" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  cfg: { type: Object, default: null },
  placeholder: { type: String, default: '' },
  multiline: { type: Boolean, default: false },
  rows: { type: Number, default: 3 }
})

const hasDecryptError = computed(() => {
  if (props.cfg?.configType !== 'secret') return false
  const v = String(props.cfg?.configValue || '')
  return v.includes('解密失败')
})

const secretPlaceholder = computed(() => {
  if (hasDecryptError.value) return '请重新填入明文，旧密文已无法解密'
  return props.placeholder || '当前已脱敏；填入明文将自动加密。留空保留原值。'
})

function toNumber(v) {
  const n = Number(v)
  return isNaN(n) ? 0 : n
}
</script>

<style scoped>
.cfg-field {
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.cfg-field:last-child {
  border-bottom: none;
}

.field-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.label-text {
  font-size: 13px;
  color: #303133;
  font-weight: 600;
}

.label-key {
  font-size: 11px;
  color: #bbb;
  font-family: monospace;
}

.field-value {
  max-width: 640px;
}

.field-hint {
  margin-top: 6px;
  font-size: 11px;
  color: #909399;
  line-height: 1.5;
}

.field-hint :deep(code) {
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  color: #d63384;
}

.field-hint :deep(a) {
  color: #409eff;
  text-decoration: underline;
}

.decrypt-warn {
  margin-top: 6px;
  font-size: 12px;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 6px 10px;
  border-radius: 6px;
  border-left: 3px solid #e6a23c;
}

.has-decrypt-error :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #e6a23c inset !important;
}
</style>

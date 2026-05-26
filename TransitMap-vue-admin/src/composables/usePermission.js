import { computed } from 'vue'
import { useUserStore } from '@/store/user'

export function usePermission() {
  const userStore = useUserStore()

  const isSuperAdmin = computed(() => userStore.roleCode === 3)
  const isRootAdmin = computed(() => userStore.roleCode === 4)
  const canEditAllFields = computed(() => isSuperAdmin.value || isRootAdmin.value)
  const canEditName = computed(() => isRootAdmin.value)

  return { isSuperAdmin, isRootAdmin, canEditAllFields, canEditName }
}

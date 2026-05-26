<template>
  <el-dialog v-model="visible" title="个人信息" width="560px" :close-on-click-modal="false" @closed="handleClosed">
    <el-tabs v-model="activeTab">
      <!-- 基本信息 -->
      <el-tab-pane label="基本信息" name="profile">
        <div class="avatar-section">
          <el-upload
            :show-file-list="false"
            :before-upload="beforeAvatarUpload"
            :http-request="handleUpload"
            accept="image/jpeg,image/png,image/gif,image/webp"
          >
            <el-badge :hidden="!avatarPreview" is-dot type="danger" style="display:none" />
            <div class="avatar-wrapper">
              <el-avatar :size="100" :src="avatarFullUrl" shape="square">
                <el-icon :size="40"><UserFilled /></el-icon>
              </el-avatar>
              <div class="avatar-overlay">
                <el-icon :size="24"><Camera /></el-icon>
                <span>更换头像</span>
              </div>
            </div>
          </el-upload>
        </div>

        <el-form :model="profileForm" label-width="80px" style="margin-top:16px">
          <el-form-item label="用户名">
            <el-input v-model="profileForm.username" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profileForm.email" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="profileForm.mobile" />
          </el-form-item>
          <el-form-item label="性别">
            <el-radio-group v-model="profileForm.genderCode">
              <el-radio :value="0">未知</el-radio>
              <el-radio :value="1">男</el-radio>
              <el-radio :value="2">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="生日">
            <el-date-picker v-model="profileForm.birthday" type="date" placeholder="选择日期"
              value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>
        </el-form>

        <div class="form-actions">
          <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">保存修改</el-button>
        </div>
      </el-tab-pane>

      <!-- 修改密码 -->
      <el-tab-pane label="修改密码" name="password">
        <el-form :model="passwordForm" label-width="100px" style="margin-top:16px">
          <el-form-item label="当前密码">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="passwordForm.newPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
          </el-form-item>
        </el-form>

        <div class="form-actions">
          <el-button type="primary" :loading="savingPassword" @click="handleSavePassword">修改密码</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled, Camera } from '@element-plus/icons-vue'
import { updateProfile, updatePassword, uploadAvatar, getUserInfo } from '@/api/user'
import { useUserStore } from '@/store/user'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])

const userStore = useUserStore()

const visible = ref(false)
const activeTab = ref('profile')

watch(() => props.modelValue, (v) => { visible.value = v })

const avatarPreview = ref('')

const avatarFullUrl = computed(() => {
  if (!avatarPreview.value) return ''
  const path = avatarPreview.value.startsWith('/') ? avatarPreview.value : '/' + avatarPreview.value
  return `/transitMap-admin${path}`
})

const profileForm = ref({
  username: '',
  email: '',
  mobile: '',
  genderCode: 0,
  birthday: '',
})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const savingProfile = ref(false)
const savingPassword = ref(false)

watch(visible, (v) => {
  if (v) {
    profileForm.value = {
      username: userStore.username || '',
      email: userStore.email || '',
      mobile: userStore.mobile || '',
      genderCode: 0,
      birthday: '',
    }
    avatarPreview.value = userStore.avatar || ''
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    activeTab.value = 'profile'

    // fetch latest user info
    getUserInfo().then(res => {
      if (res.data) {
        const d = res.data
        profileForm.value = {
          username: d.username || '',
          email: d.email || '',
          mobile: d.mobile || '',
          genderCode: d.genderCode ?? 0,
          birthday: d.birthday || '',
        }
        if (d.avatar) avatarPreview.value = d.avatar
      }
    }).catch(() => {})
  }
})

const handleClosed = () => { emit('update:modelValue', false) }

const beforeAvatarUpload = (file) => {
  const isImage = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isImage) { ElMessage.error('仅支持 JPG/PNG/GIF/WebP 格式') }
  if (!isLt5M) { ElMessage.error('头像文件不能超过 5MB') }
  return isImage && isLt5M
}

const handleUpload = async ({ file }) => {
  try {
    const res = await uploadAvatar(file)
    if (res.data && res.data.avatar) {
      avatarPreview.value = res.data.avatar
      userStore.avatar = res.data.avatar
      localStorage.setItem('avatar', res.data.avatar)
      ElMessage.success('头像已更新')
    }
  } catch { ElMessage.error('头像上传失败') }
}

const handleSaveProfile = async () => {
  savingProfile.value = true
  try {
    const data = { ...profileForm.value }
    if (!data.birthday) data.birthday = null
    await updateProfile(data)
    // 同步到 store
    userStore.username = profileForm.value.username
    userStore.email = profileForm.value.email
    userStore.mobile = profileForm.value.mobile
    localStorage.setItem('username', profileForm.value.username)
    localStorage.setItem('email', profileForm.value.email)
    localStorage.setItem('mobile', profileForm.value.mobile)
    ElMessage.success('个人信息已更新')
  } catch { } finally { savingProfile.value = false }
}

const handleSavePassword = async () => {
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.error('两次密码输入不一致')
    return
  }
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.error('密码长度不能少于6位')
    return
  }
  savingPassword.value = true
  try {
    await updatePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    })
    ElMessage.success('密码已修改')
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch { } finally { savingPassword.value = false }
}
</script>

<style scoped>
.avatar-section {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}

.avatar-wrapper {
  position: relative;
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
  font-size: 12px;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.form-actions {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>

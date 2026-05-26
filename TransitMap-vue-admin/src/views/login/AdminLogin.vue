<template>
  <div class="login-container admin-login">
    <div class="login-bg">
      <div class="metro-lines"></div>
    </div>
    <div class="login-card">
      <div class="login-header">
        <div class="logo-icon admin-icon">
          <el-icon :size="40"><UserFilled /></el-icon>
        </div>
        <h2>城市轨道交通管理系统</h2>
        <p class="subtitle">管理员登录</p>
      </div>

      <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="login-form">
        <el-form-item prop="type">
          <el-radio-group v-model="loginForm.type" class="login-type-group">
            <el-radio-button value="email">邮箱登录</el-radio-button>
            <el-radio-button value="mobile">手机号登录</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item prop="account">
          <el-input
            v-model="loginForm.account"
            :placeholder="loginForm.type === 'email' ? '请输入QQ邮箱' : '请输入手机号'"
            size="large"
            :prefix-icon="loginForm.type === 'email' ? Message : Phone"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="loginForm.captchaCode"
              placeholder="请输入验证码"
              size="large"
              :prefix-icon="Key"
              class="captcha-input"
            />
            <div class="captcha-img" @click="refreshCaptcha">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
              <span v-else>获取验证码</span>
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn admin-btn"
            :loading="loading"
            @click="handleLogin"
          >
            管理员登录
          </el-button>
        </el-form-item>

        <div class="login-footer">
          <el-link type="info" @click="$router.push('/login')">返回用户登录</el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Message, Phone, Key, UserFilled } from '@element-plus/icons-vue'
import { adminLogin, getCaptchaImage } from '@/api/user'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const loginFormRef = ref(null)
const loading = ref(false)
const captchaImage = ref('')

const loginForm = reactive({
  account: '',
  password: '',
  type: 'email',
  captchaCode: '',
  captchaKey: '',
})

const loginRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  type: [{ required: true, message: '请选择登录方式', trigger: 'change' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

const refreshCaptcha = async () => {
  try {
    const res = await getCaptchaImage()
    captchaImage.value = res.data.captchaImage
    loginForm.captchaKey = res.data.captchaKey
  } catch (e) {
    ElMessage.error('获取验证码失败')
  }
}

const handleLogin = async () => {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await adminLogin({
      account: loginForm.account,
      password: loginForm.password,
      type: loginForm.type,
      captchaCode: loginForm.captchaCode,
      captchaKey: loginForm.captchaKey,
    })
    userStore.setUser(res.data)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<style scoped>
.login-container {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.admin-login {
  background: linear-gradient(135deg, #1a1a2e 0%, #2d1b4e 50%, #441a3e 100%);
}

.login-bg {
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  z-index: 0;
}

.metro-lines {
  position: absolute;
  width: 200%;
  height: 200%;
  top: -50%;
  left: -50%;
  background:
    radial-gradient(circle at 20% 80%, rgba(160, 80, 255, 0.1) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255, 100, 150, 0.1) 0%, transparent 50%);
  animation: metroPulse 8s ease-in-out infinite;
}

@keyframes metroPulse {
  0%, 100% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.05); opacity: 1; }
}

.login-card {
  width: 420px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  width: 70px;
  height: 70px;
  margin: 0 auto 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.admin-icon {
  background: linear-gradient(135deg, #e6a23c, #f56c6c);
  box-shadow: 0 4px 15px rgba(230, 162, 60, 0.4);
}

.login-header h2 {
  font-size: 22px;
  color: #303133;
  margin-bottom: 4px;
}

.subtitle {
  color: #909399;
  font-size: 14px;
}

.login-type-group {
  width: 100%;
  display: flex;
}

.login-type-group :deep(.el-radio-button) {
  flex: 1;
}

.login-type-group :deep(.el-radio-button__inner) {
  width: 100%;
}

.captcha-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.captcha-input {
  flex: 1;
}

.captcha-img {
  width: 130px;
  height: 40px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  flex-shrink: 0;
}

.captcha-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-img span {
  font-size: 12px;
  color: #909399;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
  border: none;
}

.admin-btn {
  background: linear-gradient(135deg, #e6a23c, #f56c6c);
}

.admin-btn:hover {
  background: linear-gradient(135deg, #f0c78a, #fab6b6);
}

.login-footer {
  text-align: center;
}
</style>

<template>
  <div class="register-container">
    <div class="register-bg">
      <div class="metro-lines"></div>
    </div>
    <div class="register-card">
      <div class="register-header">
        <div class="logo-icon">
          <el-icon :size="40"><Aim /></el-icon>
        </div>
        <h2>城市轨道交通查询系统</h2>
        <p class="subtitle">用户注册</p>
      </div>

      <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" class="register-form">
        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="请输入用户名（2~20字符）"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="email">
          <el-input
            v-model="registerForm.email"
            placeholder="请输入QQ邮箱"
            size="large"
            :prefix-icon="Message"
          />
        </el-form-item>

        <el-form-item prop="emailCode">
          <div class="captcha-row">
            <el-input
              v-model="registerForm.emailCode"
              placeholder="请输入邮箱验证码"
              size="large"
              :prefix-icon="Key"
              class="captcha-input"
            />
            <el-button
              size="large"
              :disabled="emailCooldown > 0"
              @click="handleSendEmail"
              class="send-btn"
            >
              {{ emailCooldown > 0 ? `${emailCooldown}s后重试` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请确认密码"
            size="large"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-form-item prop="mobile">
          <el-input
            v-model="registerForm.mobile"
            placeholder="请输入手机号（选填）"
            size="large"
            :prefix-icon="Phone"
          />
        </el-form-item>

        <el-form-item prop="genderCode">
          <el-radio-group v-model="registerForm.genderCode">
            <el-radio :value="0">未知</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="register-btn"
            :loading="loading"
            @click="handleRegister"
          >
            注 册
          </el-button>
        </el-form-item>

        <div class="register-footer">
          <span>已有账号？</span>
          <el-link type="primary" @click="$router.push('/login')">立即登录</el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Message, Phone, Key, User, Aim } from '@element-plus/icons-vue'
import { userRegister, sendEmailCode } from '@/api/user'

const router = useRouter()
const registerFormRef = ref(null)
const loading = ref(false)
const emailCooldown = ref(0)

const registerForm = reactive({
  username: '',
  email: '',
  emailCode: '',
  password: '',
  confirmPassword: '',
  mobile: '',
  genderCode: 0,
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度必须在2~20之间', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { pattern: /^[1-9][0-9]{4,10}@qq\.com$/, message: '仅支持QQ邮箱', trigger: 'blur' },
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码必须是6位数字', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 128, message: '密码长度至少6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
  mobile: [
    { pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
}

const handleSendEmail = async () => {
  if (!registerForm.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  if (!/^[1-9][0-9]{4,10}@qq\.com$/.test(registerForm.email)) {
    ElMessage.warning('请输入正确的QQ邮箱')
    return
  }
  try {
    await sendEmailCode(registerForm.email)
    ElMessage.success('验证码已发送')
    emailCooldown.value = 60
    const timer = setInterval(() => {
      emailCooldown.value--
      if (emailCooldown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (e) {
    // error handled by interceptor
  }
}

const handleRegister = async () => {
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userRegister({
      username: registerForm.username,
      email: registerForm.email,
      emailCode: registerForm.emailCode,
      password: registerForm.password,
      mobile: registerForm.mobile || undefined,
      genderCode: registerForm.genderCode,
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  width: 100%;
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  position: relative;
  overflow: auto;
  padding: 40px 0;
}

.register-bg {
  position: fixed;
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
    radial-gradient(circle at 20% 80%, rgba(0, 150, 255, 0.1) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(0, 200, 150, 0.1) 0%, transparent 50%);
  animation: metroPulse 8s ease-in-out infinite;
}

@keyframes metroPulse {
  0%, 100% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.05); opacity: 1; }
}

.register-card {
  width: 440px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  z-index: 1;
}

.register-header {
  text-align: center;
  margin-bottom: 28px;
}

.logo-icon {
  width: 70px;
  height: 70px;
  margin: 0 auto 16px;
  background: linear-gradient(135deg, #409eff, #67c23a);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 4px 15px rgba(64, 158, 255, 0.4);
}

.register-header h2 {
  font-size: 22px;
  color: #303133;
  margin-bottom: 4px;
}

.subtitle {
  color: #909399;
  font-size: 14px;
}

.captcha-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.captcha-input {
  flex: 1;
}

.send-btn {
  width: 140px;
  flex-shrink: 0;
}

.register-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff, #337ecc);
  border: none;
}

.register-btn:hover {
  background: linear-gradient(135deg, #66b1ff, #409eff);
}

.register-footer {
  text-align: center;
  color: #909399;
  font-size: 14px;
}
</style>

<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="admin-sidebar">
      <div class="sidebar-header">
        <el-icon :size="24" color="#409eff"><Aim /></el-icon>
        <span>城市轨道管理系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#1a1a2e"
        text-color="#a0a0b0"
        active-text-color="#409eff"
      >
        <!-- 概览 -->
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon>
          <span>控制台</span>
        </el-menu-item>

        <!-- 核心业务 -->
        <div class="menu-group-label">核心业务</div>
        <el-menu-item index="/ticket-order-manage" v-if="userStore.roleCode >= 2">
          <el-icon><Ticket /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><UserFilled /></el-icon>
          <span>用户管理</span>
        </el-menu-item>

        <!-- 基础数据 -->
        <div class="menu-group-label">基础数据</div>
        <el-menu-item index="/countries">
          <el-icon><Flag /></el-icon>
          <span>国家管理</span>
        </el-menu-item>
        <el-menu-item index="/cities">
          <el-icon><OfficeBuilding /></el-icon>
          <span>城市管理</span>
        </el-menu-item>
        <el-menu-item index="/metro-lines">
          <el-icon><Van /></el-icon>
          <span>地铁线路</span>
        </el-menu-item>
        <el-menu-item index="/metro-stations">
          <el-icon><MapLocation /></el-icon>
          <span>地铁站点</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.roleCode >= 2" index="/station-line-assign">
          <el-icon><Connection /></el-icon>
          <span>站点线路分配</span>
        </el-menu-item>

        <!-- 系统工具 -->
        <div class="menu-group-label" v-if="userStore.roleCode >= 3">系统工具</div>
        <el-menu-item v-if="userStore.roleCode >= 3" index="/geocode-console">
          <el-icon><Position /></el-icon>
          <span>地理编码</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.roleCode >= 3" index="/geocode-query">
          <el-icon><Search /></el-icon>
          <span>编码查询</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.roleCode >= 3" index="/cache-manage">
          <el-icon><Coin /></el-icon>
          <span>缓存管理</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.roleCode === 4" index="/system-config">
          <el-icon><Setting /></el-icon>
          <span>系统配置</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.roleCode >= 3" index="/agent-config">
          <el-icon><Cpu /></el-icon>
          <span>Agent 配置</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.roleCode >= 2" index="/chat-manage">
          <el-icon><ChatDotRound /></el-icon>
          <span>Agent 会话</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.roleCode >= 3" index="/decrypt-approval">
          <el-icon><Lock /></el-icon>
          <span>明文审批</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.roleCode >= 2" index="/rag-knowledge">
          <el-icon><Collection /></el-icon>
          <span>RAG 知识库</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.roleCode >= 3" index="/metro-crawler">
          <el-icon><Download /></el-icon>
          <span>轨道数据爬取</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.roleCode >= 3" index="/station-review">
          <el-icon><Checked /></el-icon>
          <span>站点数据审核</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.roleCode >= 3" index="/quality-dashboard">
          <el-icon><TrendCharts /></el-icon>
          <span>对话质量看板</span>
        </el-menu-item>

        <!-- 消息中心 -->
        <div class="menu-group-label">通知</div>
        <el-menu-item v-if="userStore.roleCode >= 2" index="/messages">
          <el-icon><Bell /></el-icon>
          <span>消息中心</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div class="header-right">
          <el-icon class="header-icon" :title="isDark() ? '切换浅色' : '切换暗黑'" @click="toggle">
            <Sunny v-if="isDark()" />
            <Moon v-else />
          </el-icon>
          <el-badge v-if="msgUnreadCount > 0" :value="msgUnreadCount" :max="99" class="msg-badge">
            <el-icon class="header-icon" @click="$router.push('/messages')"><Bell /></el-icon>
          </el-badge>
          <el-icon v-else class="header-icon" @click="$router.push('/messages')"><Bell /></el-icon>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar v-if="userStore.avatar" :size="28" :src="avatarUrl" shape="square" />
              <el-icon v-else><UserFilled /></el-icon>
              {{ userStore.username }}
              <el-tag :type="roleTagType" size="small" style="margin-left: 8px">{{ userStore.role }}</el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="admin-main">
        <router-view v-slot="{ Component, route }">
          <transition name="route-fade" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </el-main>
      <ProfileDialog v-model="showProfile" />
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, Odometer, UserFilled, Flag, OfficeBuilding, Van, MapLocation, Position, Setting, Connection, Coin, Search, Ticket, Bell, ChatDotRound, Cpu, Sunny, Moon, Lock, Collection, Download, Checked, TrendCharts } from '@element-plus/icons-vue'
import { useTheme } from '@/composables/useTheme'
import { userLogout } from '@/api/user'
import { getMessageUnreadCount } from '@/api/message'
import { useUserStore } from '@/store/user'
import { useSystemConfig } from '@/composables/useSystemConfig'
import ProfileDialog from '@/views/ProfileDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { fetchConfigs } = useSystemConfig()
const { toggle, isDark } = useTheme()

const showProfile = ref(false)
const msgUnreadCount = ref(0)
let msgPollTimer = null

const fetchMsgUnread = async () => {
  try {
    const res = await getMessageUnreadCount()
    if (res.code === 200) msgUnreadCount.value = res.data.count || 0
  } catch {}
}

const avatarUrl = computed(() => {
  if (!userStore.avatar) return ''
  const path = userStore.avatar.startsWith('/') ? userStore.avatar : '/' + userStore.avatar
  return `/transitMap-admin${path}`
})

const activeMenu = computed(() => {
  if (route.path.startsWith('/users')) return '/users'
  if (route.path.startsWith('/countries')) return '/countries'
  if (route.path.startsWith('/cities') || route.path.startsWith('/city-detail')) return '/cities'
  if (route.path.startsWith('/metro-lines') || route.path.startsWith('/metro-line-detail')) return '/metro-lines'
  if (route.path.startsWith('/metro-stations') || route.path.startsWith('/metro-station-detail')) return '/metro-stations'
  if (route.path.startsWith('/station-line-assign')) return '/station-line-assign'
  if (route.path.startsWith('/geocode-console')) return '/geocode-console'
  if (route.path.startsWith('/geocode-query')) return '/geocode-query'
  if (route.path.startsWith('/cache-manage')) return '/cache-manage'
  if (route.path.startsWith('/system-config')) return '/system-config'
  if (route.path.startsWith('/ticket-order-manage')) return '/ticket-order-manage'
  if (route.path.startsWith('/metro-crawler')) return '/metro-crawler'
  if (route.path.startsWith('/station-review')) return '/station-review'
  if (route.path.startsWith('/quality-dashboard')) return '/quality-dashboard'
  if (route.path.startsWith('/messages')) return '/messages'
  return '/'
})

const roleTagType = computed(() => {
  const map = { 1: 'info', 2: 'warning', 3: 'success', 4: 'danger' }
  return map[userStore.roleCode] || 'info'
})

const handleCommand = async (command) => {
  if (command === 'profile') {
    showProfile.value = true
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      })
      await userLogout()
      userStore.clearUser()
      ElMessage.success('已退出')
      router.push('/admin/login')
    } catch (e) {
      // cancelled
    }
  }
}

onMounted(() => {
  fetchConfigs()
  fetchMsgUnread()
  msgPollTimer = setInterval(fetchMsgUnread, 30000)
})
onUnmounted(() => { if (msgPollTimer) clearInterval(msgPollTimer) })
</script>

<style scoped>
.admin-layout {
  height: 100vh;
}

.admin-sidebar {
  background: #1a1a2e;
}

.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #e0e0e0;
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.menu-group-label {
  padding: 16px 20px 6px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.25);
  text-transform: uppercase;
  letter-spacing: 1px;
  user-select: none;
}

.admin-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  border-bottom: 1px solid #ebeef5;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #606266;
}
.header-icon {
  font-size: 20px;
  color: #606266;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: all 0.2s;
}
.header-icon:hover { color: #409eff; background: #ecf5ff; }
.msg-badge { margin-right: 12px; }

.admin-main {
  background: #f5f7fa;
  overflow: auto;
}
</style>

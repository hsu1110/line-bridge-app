<template>
  <div class="common-layout">
    <el-container>
      <el-aside width="220px" class="tech-sidebar">
        <div class="logo-area">
          <div class="logo-icon">
            <img src="/LINE_New_App_Icon.png" alt="LINE" />
          </div>
          <div class="logo-text">YTEC LINE</div>
        </div>
        <el-menu
          default-active="/dashboard"
          class="el-menu-vertical-demo"
          router
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>儀表板 (Dashboard)</span>
          </el-menu-item>
          <el-menu-item index="/contacts">
            <el-icon><User /></el-icon>
            <span>通訊錄 (Contacts)</span>
          </el-menu-item>
          <el-menu-item index="/templates">
            <el-icon><Document /></el-icon>
            <span>訊息模板 (Templates)</span>
          </el-menu-item>
          <el-menu-item index="/logs">
            <el-icon><List /></el-icon>
            <span>發送紀錄 (Logs)</span>
          </el-menu-item>
          <el-menu-item index="/settings">
            <el-icon><Setting /></el-icon>
            <span>系統設定 (Settings)</span>
          </el-menu-item>
        </el-menu>
        
        <div class="sidebar-footer">
          <div class="status-dot"></div>
          <span>系統連線中</span>
        </div>
      </el-aside>
      
      <el-container>
        <el-header class="tech-header">
          <div class="header-content">
            <span class="system-id">SYS-ID: {{ systemId }}</span>
            <div class="time-display">{{ currentTime }}</div>
          </div>
        </el-header>
        <el-main class="tech-main">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Odometer, User, Document, List, Setting } from '@element-plus/icons-vue'

const currentTime = ref('')
const systemId = ref('YT-LINE-001')
let timer: any

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('en-US', { hour12: false })
}

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>

<style scoped>
.common-layout {
  height: 100vh;
  background: var(--tech-bg-dark);
}

/* Sidebar */
.tech-sidebar {
  background: rgba(5, 10, 20, 0.8);
  backdrop-filter: blur(20px);
  border-right: 1px solid var(--tech-border);
  display: flex;
  flex-direction: column;
}

.logo-area {
  height: 80px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: linear-gradient(180deg, rgba(0, 243, 255, 0.05), transparent);
}

.logo-icon {
  width: 32px;
  height: 32px;
  /* background: var(--tech-primary); Removed background */
  /* color: #fff; Removed color */
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  box-shadow: var(--tech-glow);
}

.logo-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 4px;
}

.logo-text {
  font-weight: bold;
  font-size: 16px;
  letter-spacing: 1px;
  color: var(--tech-text-main);
  text-shadow: 0 0 5px var(--tech-primary);
}

.sidebar-footer {
  margin-top: auto;
  padding: 20px;
  display: flex;
  align-items: center;
  font-size: 12px;
  color: var(--tech-text-dim);
  border-top: 1px solid var(--el-border-color-light);
}

.status-dot {
  width: 8px;
  height: 8px;
  background: var(--tech-secondary);
  border-radius: 50%;
  margin-right: 8px;
  box-shadow: 0 0 5px var(--tech-secondary);
  animation: pulse 2s infinite;
}

/* Header */
.tech-header {
  height: 60px;
  background: rgba(5, 10, 20, 0.6);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--tech-border);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 20px;
}

.header-content {
  display: flex;
  gap: 20px;
  font-family: 'Courier New', monospace;
  font-size: 14px;
  color: var(--tech-primary);
}

.tech-main {
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* Animations */
@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.5; }
  100% { opacity: 1; }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>


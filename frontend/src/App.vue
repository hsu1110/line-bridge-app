<template>
  <el-config-provider :dialog="{ alignCenter: true, draggable: true }">
    <router-view v-if="isPublicRoute" />

    <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand-header">
        <img src="/LINE_New_App_Icon.png" alt="LINE" class="brand-logo" />
        <div class="brand-name">LINE Message Hub</div>
      </div>

      <nav class="nav-list">
        <div class="nav-group-label">監控與設定</div>
        <router-link to="/dashboard" class="nav-item" active-class="is-active">
          <el-icon><Odometer /></el-icon>
          <span>總覽儀表板</span>
        </router-link>
        <router-link to="/templates" class="nav-item" active-class="is-active">
          <el-icon><Document /></el-icon>
          <span>訊息模板與測試</span>
        </router-link>
        <router-link to="/contacts" class="nav-item" active-class="is-active">
          <el-icon><User /></el-icon>
          <span>通訊錄與綁定</span>
        </router-link>

        <div class="nav-group-label">系統串接與除錯</div>
        <router-link to="/clients" class="nav-item" active-class="is-active">
          <el-icon><Key /></el-icon>
          <span>外部系統 API 金鑰</span>
        </router-link>
        <router-link to="/webhooks" class="nav-item" active-class="is-active">
          <el-icon><Connection /></el-icon>
          <span>Webhook 除錯</span>
        </router-link>
        <router-link to="/logs" class="nav-item" active-class="is-active">
          <el-icon><List /></el-icon>
          <span>發送紀錄</span>
        </router-link>
        <router-link to="/settings" class="nav-item" active-class="is-active">
          <el-icon><Setting /></el-icon>
          <span>機器人帳號設定</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="health-badge" :class="serverOnline ? 'online' : 'offline'">
          <span class="dot"></span>
          <span>{{ serverOnline ? '服務運行中' : '無法連線至後端' }}</span>
        </div>
      </div>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <div class="topbar-title">{{ pageTitle }}</div>
        <div class="topbar-actions">
          <div class="admin-pill">
            <span class="admin-avatar">{{ adminInitial }}</span>
            <span>{{ adminName }}</span>
          </div>
          <el-button size="small" plain @click="handleLogout">登出</el-button>
        </div>
      </header>

      <main class="main-content">
        <div class="content-container">
          <router-view />
        </div>
      </main>
    </div>
  </div>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Odometer,
  Document,
  User,
  Key,
  Connection,
  List,
  Setting,
} from '@element-plus/icons-vue'
import http, { authStorage } from './api/http'

const route = useRoute()
const router = useRouter()
const serverOnline = ref(true)
let healthTimer: number | undefined

const isPublicRoute = computed(() => Boolean(route.meta.public))
const pageTitle = computed(() => (route.meta.title as string) || 'LINE Message Hub')
const adminName = computed(() => authStorage.getUsername())
const adminInitial = computed(() => adminName.value.charAt(0).toUpperCase())

async function checkHealth() {
  try {
    await http.get('/status')
    serverOnline.value = true
  } catch {
    serverOnline.value = false
  }
}

function handleLogout() {
  authStorage.clear()
  router.push('/login')
}

onMounted(() => {
  checkHealth()
  healthTimer = window.setInterval(checkHealth, 30000)
})

onUnmounted(() => {
  if (healthTimer) clearInterval(healthTimer)
})
</script>

<style scoped>
.app-shell {
  --lb-header-height: 52px;
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: var(--lb-bg-canvas);
}

.sidebar {
  width: 244px;
  background: var(--lb-bg-surface);
  border-right: 1px solid var(--lb-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.brand-header {
  height: var(--lb-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  border-bottom: 1px solid var(--lb-border);
  flex-shrink: 0;
}

.brand-logo {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  object-fit: contain;
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--lb-text-primary);
  line-height: 1.25;
  white-space: nowrap;
}

.nav-list {
  flex: 1;
  padding: 14px 12px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-group-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--lb-text-muted);
  padding: 12px 10px 6px;
  letter-spacing: 0.04em;
}

.nav-group-label:first-child {
  padding-top: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 9px;
  color: var(--lb-text-secondary);
  text-decoration: none;
  font-size: 13.5px;
  font-weight: 500;
  transition: all 0.16s ease;
}

.nav-item:hover {
  background: #f1f5f9;
  color: var(--lb-text-primary);
}

.nav-item.is-active {
  background: var(--lb-primary-soft);
  color: var(--lb-primary-text);
  font-weight: 600;
}

.nav-item .el-icon {
  font-size: 17px;
}

.sidebar-footer {
  padding: 14px 16px;
  border-top: 1px solid var(--lb-border-light);
}

.health-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 500;
  padding: 8px 12px;
  border-radius: 8px;
}

.health-badge.online {
  background: #ecfdf5;
  color: #047857;
}

.health-badge.offline {
  background: #fef2f2;
  color: #b91c1c;
}

.health-badge .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: var(--lb-header-height);
  background: var(--lb-bg-surface);
  border-bottom: 1px solid var(--lb-border);
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.topbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--lb-text-primary);
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid var(--lb-border);
  font-size: 13px;
  font-weight: 500;
  color: var(--lb-text-secondary);
}

.admin-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--lb-primary);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
}

.content-container {
  max-width: 1360px;
  margin: 0 auto;
}
</style>

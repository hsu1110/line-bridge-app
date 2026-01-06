<template>
  <div class="dashboard-container">
    <!-- Top Row: Key Metrics -->
    <el-row :gutter="20" class="mb-4">
      <el-col :span="8">
        <div class="tech-card glass-panel">
          <div class="card-header">
            <span class="header-text">服務狀態</span>
            <div class="header-line"></div>
          </div>
          <div class="card-content">
            <div class="status-indicator">
              <div class="pulse-ring"></div>
              <el-tag type="success" effect="dark" class="tech-tag">運行中</el-tag>
            </div>
            <div class="tech-value">PORT: {{ port }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="tech-card glass-panel">
          <div class="card-header">
            <span class="header-text">今日發送量</span>
            <div class="header-line"></div>
          </div>
          <div class="card-content">
            <div class="digital-number">{{ todayCount }}</div>
            <div class="tech-label">MESSAGES SENT</div>
          </div>
        </div>
      </el-col>    
      <el-col :span="8">
        <div class="tech-card glass-panel">
          <div class="card-header">
            <span class="header-text">發送成功率</span>
            <div class="header-line"></div>
          </div>
          <div class="card-content">
            <div class="digital-number" :class="getRateColor(successRate)">{{ successRate }}%</div>
            <div class="tech-label">SUCCESS RATE</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Middle Row: Template Stats & Recent Logs -->
    <el-row :gutter="20" class="main-content-row">
      <!-- Template Usage Chart -->
      <el-col :span="12">
        <div class="tech-card glass-panel h-full">
          <div class="card-header">
            <span class="header-text">今日模板使用統計</span>
            <div class="header-line"></div>
          </div>
          <div class="stats-container">
            <div v-if="templateStats.length === 0" class="no-data">NO DATA</div>
            <div v-else v-for="(stat, index) in templateStats" :key="index" class="stat-item">
              <div class="stat-info">
                <span class="stat-name">{{ stat.name }}</span>
                <span class="stat-count">{{ stat.count }}</span>
              </div>
              <div class="progress-bar-bg">
                <div class="progress-bar-fill" :style="{ width: stat.percentage + '%' }"></div>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- Recent Logs -->
      <el-col :span="12">
        <div class="tech-card glass-panel h-full">
          <div class="card-header">
            <span class="header-text">即時發送監控 (最新 5 筆)</span>
            <div class="header-line"></div>
          </div>
          <div class="mini-log-list">
            <div v-if="recentLogs.length === 0" class="no-data">NO LOGS</div>
            <div v-else v-for="(log, index) in recentLogs" :key="index" class="log-item">
              <span class="log-time">{{ formatTime(log.timestamp) }}</span>
              <span class="log-target">{{ log.targetCode }}</span>
              <span class="log-status" :class="log.status === 'success' ? 'status-success' : 'status-fail'">
                {{ log.status.toUpperCase() }}
              </span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const port = ref(3000)
const todayCount = ref(0)
const successRate = ref(100)
const templateStats = ref<any[]>([])
const recentLogs = ref<any[]>([])

onMounted(async () => {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  port.value = db.settings.port
  
  const today = new Date().toISOString().split('T')[0]
  const todayLogs = db.logs.filter((l: any) => l.timestamp.startsWith(today))
  
  // 1. Today Count
  todayCount.value = todayLogs.length

  // 2. Success Rate
  if (todayCount.value > 0) {
    const successCount = todayLogs.filter((l: any) => l.status === 'success').length
    successRate.value = Math.round((successCount / todayCount.value) * 100)
  } else {
    successRate.value = 100
  }

  // 3. Template Stats
  const statsMap = new Map()
  todayLogs.forEach((l: any) => {
    const code = l.templateCode || 'UNKNOWN'
    statsMap.set(code, (statsMap.get(code) || 0) + 1)
  })
  
  templateStats.value = Array.from(statsMap.entries())
    .map(([name, count]) => ({
      name,
      count,
      percentage: Math.round((count / todayCount.value) * 100)
    }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 5) // Top 5

  // 4. Recent Logs
  recentLogs.value = db.logs
    .sort((a: any, b: any) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
    .slice(0, 5)
})

const formatTime = (isoString: string) => {
  return new Date(isoString).toLocaleTimeString('en-US', { hour12: false })
}

const getRateColor = (rate: number) => {
  if (rate >= 90) return 'text-success'
  if (rate >= 70) return 'text-warning'
  return 'text-danger'
}
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow-y: auto;
}

.mb-4 {
  margin-bottom: 20px;
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
}

.main-content-row {
  flex: 1;
  display: flex; /* Force flex layout */
  min-height: 0; /* Allow shrinking */
}

/* Ensure columns stretch to fill row */
.mb-4 :deep(.el-col),
.main-content-row :deep(.el-col) {
  display: flex;
  flex-direction: column;
}

.h-full {
  height: 100%;
  flex: 1; /* Ensure card fills column */
  min-height: 0; /* Allow internal scrolling */
}

.page-title {
  font-family: 'Courier New', monospace;
  color: var(--tech-primary);
  letter-spacing: 2px;
  margin-bottom: 20px;
  text-shadow: 0 0 10px var(--tech-primary-dim);
}

.tech-card {
  padding: 20px;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 100%; /* Ensure equal height */
}

.tech-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 20px;
  height: 20px;
  border-top: 2px solid var(--tech-primary);
  border-left: 2px solid var(--tech-primary);
}

.tech-card::after {
  content: '';
  position: absolute;
  bottom: 0;
  right: 0;
  width: 20px;
  height: 20px;
  border-bottom: 2px solid var(--tech-primary);
  border-right: 2px solid var(--tech-primary);
}

.card-header {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
  flex-shrink: 0;
}

.header-text {
  font-size: 12px;
  color: var(--tech-text-dim);
  letter-spacing: 1px;
  font-weight: bold;
}

.header-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--tech-primary), transparent);
  margin-left: 10px;
}

.card-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.digital-number {
  font-family: 'Courier New', monospace;
  font-size: 48px;
  font-weight: bold;
  color: var(--tech-primary);
  text-shadow: 0 0 15px var(--tech-primary);
}

.text-success { color: #00ff00; text-shadow: 0 0 15px #00ff00; }
.text-warning { color: #ffaa00; text-shadow: 0 0 15px #ffaa00; }
.text-danger { color: #ff0055; text-shadow: 0 0 15px #ff0055; }

.tech-value {
  font-family: 'Courier New', monospace;
  font-size: 18px;
  color: var(--tech-text-main);
  margin-top: 10px;
}

.tech-label {
  font-size: 10px;
  color: var(--tech-text-dim);
  letter-spacing: 2px;
  margin-top: 5px;
}

.status-indicator {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
}

.pulse-ring {
  position: absolute;
  width: 40px;
  height: 40px;
  border: 1px solid var(--tech-secondary);
  border-radius: 50%;
  animation: ripple 2s infinite;
}

.tech-tag {
  background: rgba(0, 255, 157, 0.1) !important;
  border: 1px solid var(--tech-secondary) !important;
  color: var(--tech-secondary) !important;
  box-shadow: 0 0 10px rgba(0, 255, 157, 0.3);
}

@keyframes ripple {
  0% { transform: scale(0.8); opacity: 1; }
  100% { transform: scale(2); opacity: 0; }
}

/* Stats List Styles */
.stats-container {
  width: 100%;
  padding: 10px;
  overflow-y: auto;
}

.stat-item {
  margin-bottom: 15px;
}

.stat-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 5px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: var(--tech-text-main);
}

.progress-bar-bg {
  width: 100%;
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background: var(--tech-primary);
  box-shadow: 0 0 10px var(--tech-primary);
  transition: width 1s ease;
}

/* Mini Log List Styles */
.mini-log-list {
  width: 100%;
  padding: 10px;
}

.log-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  font-family: 'Courier New', monospace;
  font-size: 12px;
}

.log-time { color: var(--tech-text-dim); }
.log-target { color: var(--tech-text-main); }
.status-success { color: #00ff00; text-shadow: 0 0 5px #00ff00; }
.status-fail { color: #ff0055; text-shadow: 0 0 5px #ff0055; }

.no-data {
  text-align: center;
  color: var(--tech-text-dim);
  padding: 20px;
  font-family: 'Courier New', monospace;
}
</style>

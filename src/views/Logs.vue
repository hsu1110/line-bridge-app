<template>
  <div class="page-container">
    <div class="header-actions glass-panel">
      <h2 class="page-title">系統發送紀錄</h2>
      <el-button @click="loadLogs" class="tech-btn">
        <el-icon><Refresh /></el-icon> 重新整理
      </el-button>
    </div>

    <div class="glass-panel table-container">
      <el-table :data="logs" style="width: 100%" height="100%">
        <el-table-column prop="timestamp" label="時間" width="200">
          <template #default="scope">
            <span class="tech-time">{{ new Date(scope.row.timestamp).toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Bot" width="150">
          <template #default="scope">
            <el-tag type="success" effect="plain" class="tech-tag">
              {{ getBotName(scope.row.botId) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetCode" label="目標" width="150" />
        <el-table-column prop="templateCode" label="模板" width="150" />
        <el-table-column prop="content" label="內容" show-overflow-tooltip />
        <el-table-column prop="status" label="狀態" width="120">
          <template #default="scope">
            <el-tag 
              :class="['tech-tag', scope.row.status === 'success' ? 'tech-tag-success' : 'tech-tag-danger']"
              effect="dark"
            >
              {{ scope.row.status.toUpperCase() }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="error" label="錯誤訊息" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

const logs = ref<any[]>([])
const botAccounts = ref<any[]>([])

onMounted(loadLogs)

async function loadLogs() {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  botAccounts.value = db.settings.accounts || []
  // Sort logs by timestamp desc
  logs.value = db.logs.sort((a: any, b: any) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
}

function getBotName(botId?: string) {
  if (!botId) return 'Default'
  const bot = botAccounts.value.find(b => b.id === botId || b.lineBotId === botId)
  return bot ? bot.name : 'Unknown'
}
</script>

<style scoped>
.page-container {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0; /* Prevent overflow */
  overflow: hidden;
}

.header-actions {
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.page-title {
  margin: 0;
  font-family: 'Courier New', monospace;
  color: var(--tech-primary);
  letter-spacing: 2px;
}

.table-container {
  padding: 20px;
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0; /* Critical for table scroll */
}

:deep(.el-table) {
  flex: 1;
  height: 100% !important;
}

.tech-time {
  font-family: 'Courier New', monospace;
  color: var(--tech-text-main);
}
/* ... rest of styles ... */

.tech-tag {
  background: transparent !important;
  font-family: 'Courier New', monospace;
  font-weight: bold;
}

.tech-tag-success {
  color: #00ff00 !important;
  border: 1px solid #00ff00 !important;
  text-shadow: 0 0 5px rgba(0, 255, 0, 0.5);
  box-shadow: inset 0 0 10px rgba(0, 255, 0, 0.2);
}

.tech-tag-danger {
  color: #ff0055 !important;
  border: 1px solid #ff0055 !important;
  text-shadow: 0 0 5px rgba(255, 0, 85, 0.5);
  box-shadow: inset 0 0 10px rgba(255, 0, 85, 0.2);
}

.tech-btn {
  background: transparent;
  border: 1px solid var(--tech-border);
  color: var(--tech-text-main);
}
.tech-btn:hover {
  border-color: var(--tech-primary);
  color: var(--tech-primary);
  box-shadow: var(--tech-glow);
}
</style>

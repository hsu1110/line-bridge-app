<template>
  <div class="page-shell">
    <div class="page-toolbar">
      <div>
        <h2 class="page-heading">系統運作總覽</h2>
        <p class="page-subtitle">即時監控今日訊息發送量、各官方帳號 LINE 額度與模板使用分佈</p>
      </div>
      <el-button :loading="loading" @click="loadSummary(true)">
        <el-icon><Refresh /></el-icon>
        <span>重新整理</span>
      </el-button>
    </div>

    <!-- 關鍵數據卡 -->
    <div class="kpi-grid">
      <div class="surface-card kpi-card">
        <div class="kpi-label">今日發送訊息</div>
        <div class="kpi-value">{{ summary.todayTotal }}</div>
        <div class="kpi-foot">
          <span class="pill success">成功 {{ summary.todaySuccess }}</span>
          <span v-if="summary.todayFailed > 0" class="pill danger">失敗 {{ summary.todayFailed }}</span>
        </div>
      </div>

      <div class="surface-card kpi-card">
        <div class="kpi-label">LINE 機器人數量</div>
        <div class="kpi-value">{{ summary.botCount }}</div>
        <div class="kpi-foot muted">支援多官方帳號獨立 Webhook 分流</div>
      </div>

      <div class="surface-card kpi-card">
        <div class="kpi-label">已綁定聯絡人 / 群組</div>
        <div class="kpi-value">{{ summary.contactCount }}</div>
        <div class="kpi-foot muted">依 LINE Bot 分開驗證綁定關係</div>
      </div>

      <div class="surface-card kpi-card">
        <div class="kpi-label">訊息模板 / 外部系統</div>
        <div class="kpi-value">{{ summary.templateCount }} / {{ summary.clientCount }}</div>
        <div class="kpi-foot muted">模板數量 / 授權外部系統數</div>
      </div>
    </div>

    <!-- 官方帳號即時額度 -->
    <div class="surface-card panel-box">
      <div class="panel-header">
        <div>
          <h3>各機器人本月 LINE 官方額度</h3>
          <p class="panel-note">提醒：Push 訊息至群組將依群組內人數扣減額度（例如 30 人群組發 1 次扣 30 則）</p>
        </div>
      </div>

      <div v-if="summary.botQuotas.length === 0" class="empty-block">
        尚未新增 LINE 機器人帳號，請前往「機器人帳號設定」新增。
      </div>

      <div v-else class="quota-grid">
        <div v-for="bot in summary.botQuotas" :key="bot.id" class="quota-card">
          <div class="quota-top">
            <div>
              <span class="quota-bot-name">{{ bot.name }}</span>
              <span class="mono-code" style="margin-left: 8px">{{ bot.botCode }}</span>
            </div>
            <el-tag :type="bot.enabled === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ bot.enabled === 1 ? '啟用中' : '已停用' }}
            </el-tag>
          </div>

          <div class="quota-numbers">
            <span v-if="bot.type === 'none'">不限額度方案（已發送 {{ bot.totalUsage }} 則）</span>
            <span v-else-if="bot.type === 'error'" class="text-danger">無法取得額度（請檢查 Token）</span>
            <span v-else>
              本月已使用 <strong>{{ bot.totalUsage }}</strong> / {{ bot.value }} 則
            </span>
          </div>

          <el-progress
            v-if="bot.type !== 'none' && bot.type !== 'error'"
            :percentage="calcPercentage(bot.totalUsage, bot.value)"
            :status="calcStatus(bot.totalUsage, bot.value)"
            :stroke-width="8"
            :show-text="false"
          />
        </div>
      </div>
    </div>

    <!-- 下半部：今日模板統計 & 最新發送紀錄 -->
    <div class="split-grid">
      <div class="surface-card panel-box">
        <div class="panel-header">
          <h3>今日模板使用排行</h3>
        </div>
        <div v-if="summary.templateStats.length === 0" class="empty-block">今日尚無發送紀錄</div>
        <div v-else class="template-stat-list">
          <div v-for="stat in summary.templateStats" :key="stat.name" class="stat-row">
            <div class="stat-row-top">
              <span class="mono-code">{{ stat.name }}</span>
              <span class="stat-count">{{ stat.count }} 次 ({{ stat.percentage }}%)</span>
            </div>
            <el-progress :percentage="stat.percentage" :stroke-width="7" :show-text="false" />
          </div>
        </div>
      </div>

      <div class="surface-card panel-box">
        <div class="panel-header">
          <h3>最新發送動態</h3>
          <el-button link type="primary" @click="$router.push('/logs')">查看全部</el-button>
        </div>
        <div v-if="summary.recentLogs.length === 0" class="empty-block">目前尚無發送紀錄</div>
        <div v-else class="recent-list">
          <div v-for="item in summary.recentLogs" :key="item.id" class="recent-item">
            <div class="recent-left">
              <div class="recent-target">
                <strong>{{ item.targetCode }}</strong>
                <span v-if="item.templateCode" class="mono-code" style="margin-left: 6px">{{ item.templateCode }}</span>
              </div>
              <div class="recent-meta">
                {{ formatTime(item.createdAt) }} · {{ item.clientName || '系統' }} · {{ item.botCode }}
              </div>
            </div>
            <el-tag :type="statusTagType(item.status)" size="small" effect="light">
              {{ item.status }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import http, { extractErrorMessage } from '../api/http'

interface DashboardSummary {
  todayTotal: number
  todaySuccess: number
  todayFailed: number
  botCount: number
  contactCount: number
  templateCount: number
  clientCount: number
  botQuotas: any[]
  templateStats: any[]
  recentLogs: any[]
}

const loading = ref(false)
const summary = ref<DashboardSummary>({
  todayTotal: 0,
  todaySuccess: 0,
  todayFailed: 0,
  botCount: 0,
  contactCount: 0,
  templateCount: 0,
  clientCount: 0,
  botQuotas: [],
  templateStats: [],
  recentLogs: [],
})

async function loadSummary(forceRefresh = false) {
  loading.value = true
  try {
    const res = await http.get('/admin/dashboard', {
      params: forceRefresh ? { refresh: true } : undefined,
    })
    summary.value = res.data
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  } finally {
    loading.value = false
  }
}

function calcPercentage(usage: number, limit: number) {
  if (!limit || limit <= 0) return 100
  return Math.min(Math.round((usage / limit) * 100), 100)
}

function calcStatus(usage: number, limit: number) {
  if (!limit || limit <= 0) return 'exception'
  const ratio = usage / limit
  if (ratio >= 0.9) return 'exception'
  if (ratio >= 0.75) return 'warning'
  return 'success'
}

function statusTagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

function formatTime(val: string) {
  if (!val) return '-'
  return new Date(val).toLocaleString('zh-TW', { hour12: false })
}

onMounted(() => loadSummary(false))
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.kpi-card {
  padding: 20px;
}

.kpi-label {
  font-size: 13px;
  color: var(--lb-text-secondary);
  font-weight: 500;
}

.kpi-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--lb-text-primary);
  margin: 8px 0 10px;
  letter-spacing: -0.02em;
}

.kpi-foot {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}

.kpi-foot.muted {
  color: var(--lb-text-muted);
}

.pill {
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
  font-size: 11.5px;
}

.pill.success {
  background: #ecfdf5;
  color: #047857;
}

.pill.danger {
  background: #fef2f2;
  color: #b91c1c;
}

.panel-box {
  padding: 20px 22px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.panel-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--lb-text-primary);
}

.panel-note {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--lb-text-muted);
}

.quota-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
}

.quota-card {
  padding: 16px;
  border: 1px solid var(--lb-border);
  border-radius: 10px;
  background: var(--lb-bg-subtle);
}

.quota-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.quota-bot-name {
  font-weight: 600;
  font-size: 14px;
}

.quota-numbers {
  font-size: 13px;
  color: var(--lb-text-secondary);
  margin-bottom: 10px;
}

.text-danger {
  color: #dc2626;
}

.split-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 16px;
}

.template-stat-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.stat-row-top {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 13px;
}

.stat-count {
  color: var(--lb-text-secondary);
  font-weight: 500;
}

.recent-list {
  display: flex;
  flex-direction: column;
}

.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--lb-border-light);
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-target {
  font-size: 13.5px;
  color: var(--lb-text-primary);
}

.recent-meta {
  font-size: 12px;
  color: var(--lb-text-muted);
  margin-top: 3px;
}

.empty-block {
  padding: 28px 12px;
  text-align: center;
  color: var(--lb-text-muted);
  font-size: 13px;
}
</style>

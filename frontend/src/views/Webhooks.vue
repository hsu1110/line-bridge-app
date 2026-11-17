<template>
  <div class="page-shell">
    <div class="page-toolbar">
      <div>
        <h2 class="page-heading">Webhook 除錯紀錄</h2>
        <p class="page-subtitle">即時檢視 LINE Platform 送入的 Webhook 驗簽結果、事件內容與重送去重狀態</p>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="filters.botCode" placeholder="篩選機器人" clearable style="width: 160px" @change="reloadFirst">
          <el-option v-for="b in bots" :key="b.id" :label="`${b.name} (${b.botCode})`" :value="b.botCode" />
        </el-select>
        <el-select v-model="filters.processStatus" placeholder="處理狀態" clearable style="width: 140px" @change="reloadFirst">
          <el-option label="PROCESSED" value="PROCESSED" />
          <el-option label="DUPLICATE" value="DUPLICATE" />
          <el-option label="FAILED" value="FAILED" />
          <el-option label="RECEIVED" value="RECEIVED" />
        </el-select>
        <el-button @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>重新整理</span>
        </el-button>
      </div>
    </div>

    <div class="surface-card table-card">
      <el-table :data="records" v-loading="loading" style="width: 100%">
        <el-table-column label="時間" width="175">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="Bot 代碼" width="130">
          <template #default="{ row }">
            <span class="mono-code">{{ row.botCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="事件類型" width="130">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.eventType || '-' }}</el-tag>
            <el-tag v-if="row.isRedelivery === 1" type="warning" size="small" style="margin-left: 4px">重送</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="簽章驗證" width="105">
          <template #default="{ row }">
            <el-tag :type="row.signatureValid === 1 ? 'success' : 'danger'" size="small" effect="light">
              {{ row.signatureValid === 1 ? '通過' : '失敗' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="處理狀態" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.processStatus)" size="small" effect="light">
              {{ row.processStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceId" label="來源 ID" min-width="180" show-overflow-tooltip />
        <el-table-column prop="errorMessage" label="錯誤/備註" min-width="180" show-overflow-tooltip />
        <el-table-column label="Payload" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="inspectPayload(row)">檢視 JSON</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-dialog v-model="inspectVisible" title="Webhook 原始 Payload" width="700px" destroy-on-close>
      <div v-if="currentRow?.errorMessage" style="margin-bottom: 12px">
        <el-alert :title="currentRow.errorMessage" type="error" :closable="false" show-icon />
      </div>
      <div class="code-preview-box">{{ formattedPayload }}</div>
      <template #footer>
        <el-button @click="inspectVisible = false">關閉</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import http, { extractErrorMessage } from '../api/http'
import { useDialogEsc } from '../composables/useDialogEsc'

const records = ref<any[]>([])
const bots = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({
  botCode: '',
  processStatus: '',
})

const inspectVisible = ref(false)
useDialogEsc([inspectVisible])
const currentRow = ref<any>(null)
const formattedPayload = ref('')

async function loadData() {
  loading.value = true
  try {
    const [botsRes, whRes] = await Promise.all([
      http.get('/admin/bots'),
      http.get('/admin/webhooks', {
        params: {
          page: page.value,
          size: size.value,
          botCode: filters.botCode || undefined,
          processStatus: filters.processStatus || undefined,
        },
      }),
    ])
    bots.value = botsRes.data
    records.value = whRes.data.records || []
    total.value = whRes.data.total || 0
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  } finally {
    loading.value = false
  }
}

function reloadFirst() {
  page.value = 1
  loadData()
}

function inspectPayload(row: any) {
  currentRow.value = row
  try {
    formattedPayload.value = JSON.stringify(JSON.parse(row.rawPayload || '{}'), null, 2)
  } catch {
    formattedPayload.value = row.rawPayload || ''
  }
  inspectVisible.value = true
}

function statusTag(status: string) {
  if (status === 'PROCESSED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'DUPLICATE') return 'warning'
  return 'info'
}

function formatTime(val: string) {
  if (!val) return '-'
  return new Date(val).toLocaleString('zh-TW', { hour12: false })
}

onMounted(loadData)
</script>

<style scoped>
.toolbar-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.table-card {
  padding: 16px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

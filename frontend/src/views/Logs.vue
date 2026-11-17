<template>
  <div class="page-shell">
    <div class="page-toolbar">
      <div>
        <h2 class="page-heading">訊息發送紀錄</h2>
        <p class="page-subtitle">追蹤各外部系統呼叫明細、LINE 投遞結果、耗時與錯誤詳情</p>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="filters.botCode" placeholder="篩選機器人" clearable style="width: 150px" @change="reloadFirst">
          <el-option v-for="b in bots" :key="b.id" :label="b.botCode" :value="b.botCode" />
        </el-select>
        <el-select v-model="filters.status" placeholder="發送狀態" clearable style="width: 130px" @change="reloadFirst">
          <el-option label="SUCCESS" value="SUCCESS" />
          <el-option label="FAILED" value="FAILED" />
          <el-option label="QUEUED" value="QUEUED" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="搜尋目標 / 模板 / 呼叫系統"
          clearable
          style="width: 220px"
          @keyup.enter="reloadFirst"
        />
        <el-button @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>重新整理</span>
        </el-button>
      </div>
    </div>

    <div class="surface-card table-card">
      <el-table :data="logs" v-loading="loading" style="width: 100%">
        <el-table-column label="發送時間" width="175">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="clientName" label="呼叫來源" width="130" show-overflow-tooltip />
        <el-table-column label="Bot" width="110">
          <template #default="{ row }">
            <span class="mono-code">{{ row.botCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="接收目標" width="150">
          <template #default="{ row }">
            <span class="mono-code">{{ row.targetCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="模板" width="140">
          <template #default="{ row }">
            <span v-if="row.templateCode" class="mono-code">{{ row.templateCode }}</span>
            <span v-else class="muted-text">直接內容</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="訊息內容" min-width="220" show-overflow-tooltip />
        <el-table-column label="耗時" width="85">
          <template #default="{ row }">{{ row.durationMs != null ? `${row.durationMs}ms` : '-' }}</template>
        </el-table-column>
        <el-table-column label="狀態" width="105">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small" effect="light">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="錯誤資訊" min-width="180" show-overflow-tooltip />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import http, { extractErrorMessage } from '../api/http'

const logs = ref<any[]>([])
const bots = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({
  botCode: '',
  status: '',
  keyword: '',
})

async function loadData() {
  loading.value = true
  try {
    const [botsRes, logsRes] = await Promise.all([
      http.get('/admin/bots'),
      http.get('/admin/logs', {
        params: {
          page: page.value,
          size: size.value,
          botCode: filters.botCode || undefined,
          status: filters.status || undefined,
          keyword: filters.keyword || undefined,
        },
      }),
    ])
    bots.value = botsRes.data
    logs.value = logsRes.data.records || []
    total.value = logsRes.data.total || 0
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

function statusTag(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'warning'
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

.muted-text {
  color: var(--lb-text-muted);
  font-size: 12px;
}
</style>

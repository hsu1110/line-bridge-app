<template>
  <div class="page-shell">
    <div class="page-toolbar">
      <div>
        <h2 class="page-heading">外部系統 API 金鑰管理</h2>
        <p class="page-subtitle">為公司各個呼叫系統核發獨立 API Key，控管允許使用的機器人、模板與每分鐘發送上限</p>
      </div>
      <div class="toolbar-actions">
        <el-button @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>重新整理</span>
        </el-button>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon>
          <span>新增外部系統</span>
        </el-button>
      </div>
    </div>

    <div class="surface-card table-card">
      <el-table :data="clients" v-loading="loading" style="width: 100%">
        <el-table-column prop="clientName" label="系統名稱" min-width="140" />
        <el-table-column label="系統代碼" width="130">
          <template #default="{ row }">
            <span class="mono-code">{{ row.clientCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="API Key (x-api-key)" min-width="370">
          <template #default="{ row }">
            <div class="key-cell">
              <span class="mono-code key-text">{{ revealed[row.id] ? row.apiKey : maskKey(row.apiKey) }}</span>
              <div class="key-actions">
                <el-button link size="small" @click="revealed[row.id] = !revealed[row.id]">
                  {{ revealed[row.id] ? '隱藏' : '顯示' }}
                </el-button>
                <el-button link type="primary" size="small" @click="copyKey(row.apiKey)">複製</el-button>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="允許的 Bot" min-width="160">
          <template #default="{ row }">
            <div class="tag-wrap">
              <template v-if="splitCodes(row.allowedBotCodes).length > 0">
                <el-tag v-for="code in splitCodes(row.allowedBotCodes)" :key="code" size="small" type="success" effect="plain">
                  {{ code }}
                </el-tag>
              </template>
              <el-tag v-else size="small" type="info" effect="light">全部允許</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="允許的模板" min-width="180">
          <template #default="{ row }">
            <div class="tag-wrap">
              <template v-if="splitCodes(row.allowedTemplateCodes).length > 0">
                <el-tag v-for="code in splitCodes(row.allowedTemplateCodes)" :key="code" size="small" effect="plain">
                  {{ code }}
                </el-tag>
              </template>
              <el-tag v-else size="small" type="info" effect="light">全部允許</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="速率上限" width="105">
          <template #default="{ row }">{{ row.rateLimitPerMin }} 次/分</template>
        </el-table-column>
        <el-table-column label="狀態" width="85">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ row.enabled === 1 ? '啟用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">編輯</el-button>
            <el-button size="small" type="warning" plain @click="rotateKey(row)">輪換金鑰</el-button>
            <el-button size="small" type="danger" plain @click="deleteClient(row)">刪除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '編輯外部系統' : '新增外部系統'" width="580px" destroy-on-close>
      <el-form :model="form" label-width="135px">
        <el-form-item label="系統名稱">
          <el-input v-model="form.clientName" placeholder="例如：設備告警監控系統" />
        </el-form-item>
        <el-form-item label="系統代碼">
          <el-input v-model="form.clientCode" placeholder="例如：MONITOR_SYS" />
        </el-form-item>
        <el-form-item label="允許的 Bot 代碼">
          <el-select
            v-model="form.allowedBotCodeList"
            multiple
            filterable
            clearable
            placeholder="不選代表允許使用所有機器人"
            style="width: 100%"
          >
            <el-option
              v-for="b in bots"
              :key="b.id"
              :label="`${b.name} (${b.botCode})`"
              :value="b.botCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="允許的模板代碼">
          <el-select
            v-model="form.allowedTemplateCodeList"
            multiple
            filterable
            clearable
            placeholder="不選代表允許使用所有模板"
            style="width: 100%"
          >
            <el-option
              v-for="t in templates"
              :key="t.id"
              :label="`${t.name} (${t.code})`"
              :value="t.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="每分鐘呼叫上限">
          <el-input-number v-model="form.rateLimitPerMin" :min="0" :max="10000" />
        </el-form-item>
        <el-form-item label="啟用狀態">
          <el-switch v-model="form.enabledBool" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveClient">儲存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import http, { extractErrorMessage } from '../api/http'
import { useDialogEsc } from '../composables/useDialogEsc'

const clients = ref<any[]>([])
const bots = ref<any[]>([])
const templates = ref<any[]>([])
const loading = ref(false)
const revealed = reactive<Record<string, boolean>>({})

const dialogVisible = ref(false)
useDialogEsc([dialogVisible])
const isEdit = ref(false)
const saving = ref(false)
const editingId = ref('')
const form = reactive({
  clientCode: '',
  clientName: '',
  allowedBotCodeList: [] as string[],
  allowedTemplateCodeList: [] as string[],
  rateLimitPerMin: 60,
  enabledBool: true,
})

function splitCodes(raw?: string): string[] {
  if (!raw) return []
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

async function loadData() {
  loading.value = true
  try {
    const [clientsRes, botsRes, templatesRes] = await Promise.all([
      http.get('/admin/clients'),
      http.get('/admin/bots'),
      http.get('/admin/templates'),
    ])
    clients.value = clientsRes.data
    bots.value = botsRes.data
    templates.value = templatesRes.data
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  } finally {
    loading.value = false
  }
}

function maskKey(key: string) {
  if (!key || key.length <= 10) return '••••••••'
  return `${key.slice(0, 8)}••••••••${key.slice(-4)}`
}

async function copyKey(key: string) {
  await navigator.clipboard.writeText(key)
  ElMessage.success('已複製 API Key')
}

function openDialog(row?: any) {
  if (row) {
    isEdit.value = true
    editingId.value = row.id
    form.clientCode = row.clientCode
    form.clientName = row.clientName
    form.allowedBotCodeList = splitCodes(row.allowedBotCodes)
    form.allowedTemplateCodeList = splitCodes(row.allowedTemplateCodes)
    form.rateLimitPerMin = row.rateLimitPerMin ?? 60
    form.enabledBool = row.enabled === 1
  } else {
    isEdit.value = false
    editingId.value = ''
    form.clientCode = ''
    form.clientName = ''
    form.allowedBotCodeList = []
    form.allowedTemplateCodeList = []
    form.rateLimitPerMin = 60
    form.enabledBool = true
  }
  dialogVisible.value = true
}

async function saveClient() {
  saving.value = true
  try {
    const payload = {
      clientCode: form.clientCode,
      clientName: form.clientName,
      allowedBotCodes: form.allowedBotCodeList.join(','),
      allowedTemplateCodes: form.allowedTemplateCodeList.join(','),
      rateLimitPerMin: form.rateLimitPerMin,
      enabled: form.enabledBool ? 1 : 0,
    }
    if (isEdit.value) {
      await http.put(`/admin/clients/${editingId.value}`, payload)
    } else {
      await http.post('/admin/clients', payload)
    }
    ElMessage.success('外部系統設定已儲存')
    dialogVisible.value = false
    await loadData()
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  } finally {
    saving.value = false
  }
}

async function rotateKey(row: any) {
  try {
    await ElMessageBox.confirm(`確定要重新產生「${row.clientName}」的 API Key 嗎？舊金鑰將立即失效。`, '輪換確認', {
      type: 'warning',
      confirmButtonText: '立即輪換',
      cancelButtonText: '取消',
    })
    await http.post(`/admin/clients/${row.id}/rotate-key`)
    revealed[row.id] = true
    ElMessage.success('已更新 API Key')
    await loadData()
  } catch (err: any) {
    if (err !== 'cancel') ElMessage.error(extractErrorMessage(err))
  }
}

async function deleteClient(row: any) {
  try {
    await ElMessageBox.confirm(`確定要刪除「${row.clientName}」嗎？`, '刪除確認', {
      type: 'warning',
      confirmButtonText: '刪除',
      cancelButtonText: '取消',
    })
    await http.delete(`/admin/clients/${row.id}`)
    ElMessage.success('已刪除外部系統')
    await loadData()
  } catch (err: any) {
    if (err !== 'cancel') ElMessage.error(extractErrorMessage(err))
  }
}

onMounted(loadData)
</script>

<style scoped>
.toolbar-actions {
  display: flex;
  gap: 10px;
}

.table-card {
  padding: 16px;
}

.key-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.key-text {
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  white-space: nowrap;
}

.key-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.tag-wrap {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>

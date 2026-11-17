<template>
  <div class="page-shell">
    <div class="page-toolbar">
      <div>
        <h2 class="page-heading">訊息模板與發送測試</h2>
        <p class="page-subtitle">定義模板代碼與 <span class="mono-code" v-pre>{{變數}}</span> 佔位符，支援即時渲染試算與同步測試發送</p>
      </div>
      <div class="toolbar-actions">
        <el-button @click="loadTemplates">
          <el-icon><Refresh /></el-icon>
          <span>重新整理</span>
        </el-button>
        <el-button type="primary" @click="openEditor()">
          <el-icon><Plus /></el-icon>
          <span>新增模板</span>
        </el-button>
      </div>
    </div>

    <div class="surface-card table-card">
      <el-table :data="templates" v-loading="loading" style="width: 100%">
        <el-table-column prop="name" label="模板名稱" min-width="160" />
        <el-table-column label="呼叫代碼 (template_code)" min-width="180">
          <template #default="{ row }">
            <span class="mono-code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="必填參數" min-width="200">
          <template #default="{ row }">
            <div class="tag-wrap">
              <el-tag v-for="v in parseVars(row.requiredVars)" :key="v" size="small" effect="plain">
                {{ v }}
              </el-tag>
              <span v-if="parseVars(row.requiredVars).length === 0" class="muted-text">無參數</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="訊息內容預覽" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="openTestDialog(row)">發送測試</el-button>
            <el-button size="small" @click="openEditor(row)">編輯</el-button>
            <el-button size="small" type="danger" plain @click="deleteTemplate(row)">刪除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增 / 編輯模板（含即時預覽） -->
    <el-dialog v-model="editorVisible" :title="isEdit ? '編輯訊息模板' : '新增訊息模板'" width="860px" destroy-on-close>
      <div class="editor-grid">
        <el-form :model="editorForm" label-position="top">
          <el-form-item label="模板名稱">
            <el-input v-model="editorForm.name" placeholder="例如：機台異常告警" />
          </el-form-item>
          <el-form-item label="呼叫代碼 (template_code)">
            <el-input v-model="editorForm.code" placeholder="例如：MACHINE_ERROR（供 API 呼叫用）" />
          </el-form-item>
          <el-form-item label="說明備註">
            <el-input v-model="editorForm.description" placeholder="選填，用途或呼叫時機說明" />
          </el-form-item>
          <el-form-item label="訊息內容（使用 {{變數名稱}} 插入動態欄位）">
            <el-input
              v-model="editorForm.content"
              type="textarea"
              :rows="6"
              placeholder="範例：機台 {{machine_id}} 於 {{time}} 發生異常：{{reason}}"
              @input="runPreview"
            />
          </el-form-item>
        </el-form>

        <div class="preview-pane">
          <div class="preview-title">即時渲染預覽 (Dry Run)</div>
          <div v-if="detectedVars.length > 0" class="preview-inputs">
            <div v-for="v in detectedVars" :key="v" class="preview-input-row">
              <span class="mono-code">{{ v }}</span>
              <el-input v-model="previewParams[v]" size="small" placeholder="輸入測試值" @input="runPreview" />
            </div>
          </div>
          <div v-else class="muted-text" style="margin-bottom: 12px">目前內容未包含任何變數佔位符</div>

          <div class="line-bubble-stage">
            <div class="line-bubble">{{ previewOutput || '（尚未輸入內容）' }}</div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTemplate">儲存模板</el-button>
      </template>
    </el-dialog>

    <!-- 測試發送 & API 呼叫範例 -->
    <el-dialog v-model="testVisible" title="發送測試與 API 串接範例" width="760px" destroy-on-close>
      <el-form :model="testForm" label-width="110px">
        <el-form-item label="發送機器人">
          <el-select v-model="testForm.botCode" placeholder="請選擇機器人" style="width: 100%" @change="onTestBotChange">
            <el-option v-for="b in bots" :key="b.id" :label="`${b.name} (${b.botCode})`" :value="b.botCode" />
          </el-select>
        </el-form-item>

        <el-form-item label="接收目標">
          <el-select
            v-model="testForm.targetCode"
            placeholder="請選擇已綁定此機器人的目標"
            style="width: 100%"
            filterable
            :disabled="!testForm.botCode"
          >
            <el-option
              v-for="c in filteredContacts"
              :key="c.id"
              :label="`${c.name} (${c.code || c.lineId})`"
              :value="c.code || c.lineId"
            />
          </el-select>
          <div class="field-hint">僅列出已與所選機器人完成綁定的聯絡人／群組</div>
        </el-form-item>

        <template v-if="testVars.length > 0">
          <el-divider content-position="left">模板參數</el-divider>
          <el-form-item v-for="v in testVars" :key="v" :label="v">
            <el-input v-model="testForm.params[v]" :placeholder="`請輸入 ${v}`" />
          </el-form-item>
        </template>

        <el-alert
          v-if="testResult"
          :title="testResult.success ? '發送成功' : '發送失敗'"
          :type="testResult.success ? 'success' : 'error'"
          :description="testResult.message"
          show-icon
          :closable="false"
          style="margin-top: 12px"
        />

        <div class="snippet-header">
          <span>外部系統呼叫範例 (POST /api/send)</span>
          <el-radio-group v-model="snippetMode" size="small">
            <el-radio-button value="powershell">Windows PowerShell (UTF-8)</el-radio-button>
            <el-radio-button value="curl">Linux / Bash (curl)</el-radio-button>
          </el-radio-group>
        </div>
        <div class="code-preview-box">{{ apiExampleSnippet }}</div>
      </el-form>

      <template #footer>
        <el-button @click="copySnippet">複製 API 範例</el-button>
        <el-button @click="testVisible = false">關閉</el-button>
        <el-button type="primary" :loading="sending" @click="submitTestSend">立即測試發送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import http, { extractErrorMessage } from '../api/http'
import { useDialogEsc } from '../composables/useDialogEsc'

const templates = ref<any[]>([])
const bots = ref<any[]>([])
const contacts = ref<any[]>([])
const loading = ref(false)

// 編輯器狀態
const editorVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const editingId = ref('')
const editorForm = reactive({
  code: '',
  name: '',
  description: '',
  content: '',
})
const previewParams = reactive<Record<string, string>>({})
const detectedVars = ref<string[]>([])
const previewOutput = ref('')

// 測試視窗狀態
const testVisible = ref(false)
useDialogEsc([editorVisible, testVisible])
const snippetMode = ref<'powershell' | 'curl'>('powershell')
const sending = ref(false)
const currentTemplate = ref<any>(null)
const testVars = ref<string[]>([])
const testForm = reactive({
  botCode: '',
  targetCode: '',
  params: {} as Record<string, string>,
})
const testResult = ref<{ success: boolean; message: string } | null>(null)

function parseVars(raw: string): string[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

async function loadTemplates() {
  loading.value = true
  try {
    const res = await http.get('/admin/templates')
    templates.value = res.data
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  } finally {
    loading.value = false
  }
}

function openEditor(row?: any) {
  Object.keys(previewParams).forEach((k) => delete previewParams[k])
  if (row) {
    isEdit.value = true
    editingId.value = row.id
    editorForm.code = row.code
    editorForm.name = row.name
    editorForm.description = row.description || ''
    editorForm.content = row.content
  } else {
    isEdit.value = false
    editingId.value = ''
    editorForm.code = ''
    editorForm.name = ''
    editorForm.description = ''
    editorForm.content = ''
  }
  runPreview()
  editorVisible.value = true
}

async function runPreview() {
  try {
    const res = await http.post('/admin/templates/preview', {
      content: editorForm.content,
      params: previewParams,
    })
    detectedVars.value = res.data.variables || []
    previewOutput.value = res.data.renderedContent || ''
  } catch {
    previewOutput.value = editorForm.content
  }
}

async function saveTemplate() {
  saving.value = true
  try {
    if (isEdit.value) {
      await http.put(`/admin/templates/${editingId.value}`, editorForm)
    } else {
      await http.post('/admin/templates', editorForm)
    }
    ElMessage.success('模板已儲存')
    editorVisible.value = false
    await loadTemplates()
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  } finally {
    saving.value = false
  }
}

async function deleteTemplate(row: any) {
  try {
    await ElMessageBox.confirm(`確定要刪除模板「${row.name}」嗎？`, '刪除確認', {
      type: 'warning',
      confirmButtonText: '刪除',
      cancelButtonText: '取消',
    })
    await http.delete(`/admin/templates/${row.id}`)
    ElMessage.success('已刪除模板')
    await loadTemplates()
  } catch (err: any) {
    if (err !== 'cancel') ElMessage.error(extractErrorMessage(err))
  }
}

const filteredContacts = computed(() => {
  const bot = bots.value.find((b) => b.botCode === testForm.botCode)
  if (!bot) return []
  return contacts.value.filter((c) => Array.isArray(c.linkedBotIds) && c.linkedBotIds.includes(bot.id))
})

async function openTestDialog(row: any) {
  currentTemplate.value = row
  testResult.value = null
  testVars.value = parseVars(row.requiredVars)
  testForm.params = {}
  testVars.value.forEach((v) => {
    testForm.params[v] = ''
  })

  try {
    const [botsRes, contactsRes] = await Promise.all([
      http.get('/admin/bots'),
      http.get('/admin/contacts'),
    ])
    bots.value = botsRes.data
    contacts.value = contactsRes.data
    testForm.botCode = bots.value.length > 0 ? bots.value[0].botCode : ''
    onTestBotChange()
    testVisible.value = true
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  }
}

function onTestBotChange() {
  testForm.targetCode = filteredContacts.value.length > 0
    ? (filteredContacts.value[0].code || filteredContacts.value[0].lineId)
    : ''
}

async function submitTestSend() {
  if (!testForm.botCode || !testForm.targetCode) {
    ElMessage.warning('請選擇機器人與接收目標')
    return
  }
  sending.value = true
  testResult.value = null
  try {
    const selected = contacts.value.find((c) => c.code === testForm.targetCode || c.lineId === testForm.targetCode)
    const payload: Record<string, any> = {
      bot_code: testForm.botCode,
      template_code: currentTemplate.value.code,
      params: testForm.params,
    }
    if (selected?.code) {
      payload.target_code = selected.code
    } else {
      payload.to = testForm.targetCode
    }
    const res = await http.post('/admin/templates/test-send', payload)
    testResult.value = {
      success: true,
      message: `Job ID: ${res.data.jobId}\n送出內容：${res.data.renderedContent}`,
    }
  } catch (err) {
    testResult.value = { success: false, message: extractErrorMessage(err) }
  } finally {
    sending.value = false
  }
}

const apiExampleSnippet = computed(() => {
  const sampleParams: Record<string, string> = {}
  testVars.value.forEach((v) => {
    sampleParams[v] = testForm.params[v] || `<${v}>`
  })
  const body = {
    bot_code: testForm.botCode || 'IT_BOT',
    target_code: testForm.targetCode || 'IT_ALERT',
    template_code: currentTemplate.value?.code || 'TEMPLATE_CODE',
    params: sampleParams,
  }
  const jsonText = JSON.stringify(body, null, 2)
  const targetUrl = `${window.location.origin}/api/send`
  if (snippetMode.value === 'powershell') {
    return `$headers = @{ "x-api-key" = "YOUR_API_KEY" }\n`
      + `$json = @'\n${jsonText}\n'@\n`
      + `$bytes = [System.Text.Encoding]::UTF8.GetBytes($json)\n`
      + `Invoke-RestMethod -Uri "${targetUrl}" -Method Post -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bytes`
  }
  return `curl -X POST ${targetUrl} \\\n`
    + `  -H "Content-Type: application/json; charset=utf-8" \\\n`
    + `  -H "x-api-key: YOUR_API_KEY" \\\n`
    + `  -d '${jsonText}'`
})

async function copySnippet() {
  await navigator.clipboard.writeText(apiExampleSnippet.value)
  ElMessage.success('已複製 API 呼叫範例')
}

onMounted(loadTemplates)
</script>

<style scoped>
.toolbar-actions {
  display: flex;
  gap: 10px;
}

.table-card {
  padding: 16px;
}

.tag-wrap {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.muted-text {
  color: var(--lb-text-muted);
  font-size: 12.5px;
}

.editor-grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 24px;
}

.preview-pane {
  background: var(--lb-bg-subtle);
  border: 1px solid var(--lb-border);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.preview-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--lb-text-primary);
  margin-bottom: 12px;
}

.preview-inputs {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}

.preview-input-row {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 8px;
  align-items: center;
}

.line-bubble-stage {
  flex: 1;
  background: #84a2c7;
  border-radius: 10px;
  padding: 16px;
  min-height: 150px;
}

.line-bubble {
  display: inline-block;
  max-width: 100%;
  background: #ffffff;
  color: #111827;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 13.5px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.12);
}

.field-hint {
  font-size: 12px;
  color: var(--lb-text-muted);
  margin-top: 4px;
}

.snippet-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 18px 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--lb-text-secondary);
}
</style>

<template>
  <div class="page-container">
    <div class="header-actions glass-panel">
      <h2 class="page-title">訊息模板管理</h2>
      <el-button type="primary" @click="openAddDialog">
        <el-icon><Plus /></el-icon> 新增模板
      </el-button>
    </div>

    <div class="glass-panel table-container">
      <el-table :data="templates" style="width: 100%" height="100%">
        <el-table-column prop="name" label="模板名稱" />
        <el-table-column prop="code" label="代碼 (Code)" />
        <el-table-column prop="content" label="內容預覽" show-overflow-tooltip />
        <el-table-column label="操作" width="220" align="right">
          <template #default="scope">
            <el-button size="small" type="success" @click="openTestDialog(scope.row)" class="tech-btn">測試</el-button>
            <el-button size="small" @click="editTemplate(scope.row)" class="tech-btn">編輯</el-button>
            <el-button size="small" type="danger" @click="deleteTemplate(scope.row.id)" class="tech-btn">刪除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Add/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '編輯模板' : '新增模板'" class="tech-dialog">
      <el-form :model="form" label-width="120px" class="tech-form">
        <el-form-item label="名稱">
          <el-input v-model="form.name" placeholder="例如：機台異常通知" />
        </el-form-item>
        <el-form-item label="代碼">
          <el-input v-model="form.code" placeholder="外部呼叫用 (如 MACHINE_ERROR)" />
        </el-form-item>
        <el-form-item label="內容">
          <el-input 
            v-model="form.content" 
            type="textarea" 
            rows="5" 
            placeholder="支援變數，例如：機台 {{machine_id}} 發生異常！" 
          />
          <div class="tip" v-pre>使用 {{variable}} 來設定變數</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTemplate">儲存</el-button>
      </template>
    </el-dialog>

    <!-- Test Send Dialog -->
    <el-dialog v-model="testDialogVisible" title="測試發送" width="50%" class="tech-dialog">
      <el-form :model="testForm" label-width="100px" class="tech-form">
        <el-form-item label="發送目標">
          <el-select v-model="testForm.target_code" placeholder="請選擇目標" style="width: 100%">
            <el-option 
              v-for="contact in contactList" 
              :key="contact.id" 
              :label="contact.name + ' (' + contact.code + ')'" 
              :value="contact.code" 
            />
          </el-select>
        </el-form-item>
        
        <div v-if="testParams.length > 0">
          <el-divider content-position="left" class="tech-divider">模板參數</el-divider>
          <el-form-item v-for="param in testParams" :key="param" :label="param">
            <el-input v-model="testForm.params[param]" :placeholder="'請輸入 ' + param" />
          </el-form-item>
        </div>

        <el-alert
          v-if="testResult"
          :title="testResult.success ? '發送成功' : '發送失敗'"
          :type="testResult.success ? 'success' : 'error'"
          :description="testResult.message"
          show-icon
          style="margin-top: 20px"
          effect="dark"
        />
      </el-form>
      <template #footer>
        <el-button @click="testDialogVisible = false">關閉</el-button>
        <el-button type="primary" :loading="sending" @click="sendTestMessage">確認發送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { v4 as uuidv4 } from 'uuid'
import axios from 'axios'

const templates = ref<any[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<any>({})

// Test Send Related
const testDialogVisible = ref(false)
const testForm = ref<any>({ target_code: '', params: {} })
const contactList = ref<any[]>([])
const testParams = ref<string[]>([])
const sending = ref(false)
const testResult = ref<any>(null)
const currentTemplateCode = ref('')

onMounted(loadTemplates)

async function loadTemplates() {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  templates.value = db.templates
}

function openAddDialog() {
  isEdit.value = false
  form.value = {}
  dialogVisible.value = true
}

function editTemplate(row: any) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function saveTemplate() {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  const formData = { ...form.value }
  if (isEdit.value) {
    const index = db.templates.findIndex((t: any) => t.id === formData.id)
    if (index !== -1) db.templates[index] = formData
  } else {
    formData.id = uuidv4()
    db.templates.push(formData)
  }
  await (window as any).ipcRenderer.invoke('db:save', db)
  dialogVisible.value = false
  loadTemplates()
  ElMessage.success('SAVED')
}

async function deleteTemplate(id: string) {
  if (!confirm('DELETE TEMPLATE?')) return
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  db.templates = db.templates.filter((t: any) => t.id !== id)
  await (window as any).ipcRenderer.invoke('db:save', db)
  loadTemplates()
  ElMessage.success('DELETED')
}

// Test Send Logic
async function openTestDialog(row: any) {
  // Load contacts for selection
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  contactList.value = db.contacts
  
  currentTemplateCode.value = row.code
  testForm.value = { target_code: '', params: {} }
  testResult.value = null
  
  // Parse params from content (e.g. {{machine_id}})
  const matches = row.content.match(/{{(.*?)}}/g)
  testParams.value = matches ? matches.map((m: string) => m.replace(/{{|}}/g, '').trim()) : []
  
  testDialogVisible.value = true
}

async function sendTestMessage() {
  if (!testForm.value.target_code) {
    return ElMessage.warning('SELECT TARGET')
  }
  
  sending.value = true
  testResult.value = null
  
  try {
    // Call internal API
    const db = await (window as any).ipcRenderer.invoke('db:get-all')
    const port = db.settings.port || 3000
    
    const payload = {
      target_code: testForm.value.target_code,
      template_code: currentTemplateCode.value,
      params: testForm.value.params
    }
    
    const config = {
      headers: {
        'x-api-key': db.settings.apiKey
      }
    }
    
    const res = await axios.post(`http://localhost:${port}/api/send`, payload, config)
    
    if (res.data.success) {
      testResult.value = { success: true, message: 'SENT! Log ID: ' + res.data.logId }
      ElMessage.success('SENT')
    }
  } catch (e: any) {
    const errorMsg = e.response?.data?.error || e.message
    testResult.value = { success: false, message: 'FAILED: ' + errorMsg }
    ElMessage.error('FAILED')
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.page-container {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
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
  min-height: 0;
}

:deep(.el-table) {
  flex: 1;
  height: 100% !important;
}

.tip {
  font-size: 12px;
  color: var(--tech-text-dim);
  margin-top: 5px;
}
/* ... rest of styles ... */

.tech-divider {
  border-color: var(--tech-border);
}

.tech-divider .el-divider__text {
  background-color: var(--tech-bg-panel);
  color: var(--tech-primary);
}
</style>

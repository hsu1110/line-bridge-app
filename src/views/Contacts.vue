<template>
  <div class="page-container">
    <div class="header-actions glass-panel">
      <h2 class="page-title">通訊錄管理</h2>
      <div class="action-buttons">
        <el-button @click="loadContacts" class="tech-btn">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
        <el-button v-if="bindingMethod === 'gas'" type="success" @click="openImportDialog">
          <el-icon><Download /></el-icon> 從 GAS 匯入
        </el-button>
        <el-button type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon> 新增聯絡人
        </el-button>
      </div>
    </div>

    <div class="glass-panel table-container">
      <el-table :data="contacts" style="width: 100%" height="100%">
        <el-table-column prop="name" label="名稱" />
        <el-table-column prop="code" label="代碼 (Code)">
          <template #default="scope">
            <div class="code-cell">
              <span>{{ scope.row.code }}</span>
              <el-tooltip
                v-if="isDuplicateCode(scope.row.code)"
                content="代碼重複！請修改以免發送錯誤"
                placement="top"
              >
                <el-tag type="danger" class="duplicate-tag">
                  <el-icon size="large"><WarningFilled /></el-icon> 重複
                </el-tag>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="類型">
          <template #default="scope">
            <el-tag :type="scope.row.type === 'group' ? 'warning' : 'info'">
              {{ scope.row.type === 'group' ? '群組' : '個人' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lineId" label="LINE ID" show-overflow-tooltip />
        <el-table-column label="操作" width="180" align="right">
          <template #default="scope">
            <el-button size="small" @click="editContact(scope.row)" class="tech-btn">編輯</el-button>
            <el-button size="small" type="danger" @click="deleteContact(scope.row.id)" class="tech-btn">刪除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Import Dialog -->
    <el-dialog v-model="importDialogVisible" title="從 GAS 匯入" width="60%" class="tech-dialog">
      <div v-if="loadingGas" class="loading-container">
        <div class="tech-loader"></div>
        <div class="loading-text">資料同步中...</div>
      </div>
      <div v-else>
        <el-table :data="gasData" @selection-change="handleSelectionChange" height="300px">
          <el-table-column type="selection" width="55" />
          <el-table-column prop="name" label="名稱" />
          <el-table-column prop="id" label="ID" show-overflow-tooltip />
          <el-table-column prop="type" label="類型" />
          <el-table-column label="狀態" width="100">
             <template #default="scope">
               <el-tag v-if="scope.row.status === 'update'" type="warning" size="small">更新</el-tag>
               <el-tag v-else type="success" size="small">新增</el-tag>
             </template>
          </el-table-column>
        </el-table>
        <div class="dialog-footer">
          <el-button @click="importDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmImport">匯入選取項目</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- Add/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '編輯聯絡人' : '新增聯絡人'" width="500px">
      <el-form :model="form" label-width="100px" class="tech-form">
        <el-form-item label="名稱">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="代碼">
          <el-input v-model="form.code" placeholder="外部系統呼叫用的代號 (如 IT_ALERT)" />
        </el-form-item>
        <el-form-item label="類型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="個人 (User)" value="user" />
            <el-option label="群組 (Group)" value="group" />
          </el-select>
        </el-form-item>
        <el-form-item label="LINE ID">
          <el-input v-model="form.lineId" :disabled="isEdit" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveContact">儲存資料</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Plus, WarningFilled, Refresh } from '@element-plus/icons-vue'
import { v4 as uuidv4 } from 'uuid'

const contacts = ref<any[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<any>({})
const bindingMethod = ref('gas')

// Import related
const importDialogVisible = ref(false)
const loadingGas = ref(false)
const gasData = ref<any[]>([])
const selectedImport = ref<any[]>([])

onMounted(loadContacts)

async function loadContacts() {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  contacts.value = db.contacts
  bindingMethod.value = db.settings.bindingMethod || 'gas'
}

// Duplicate Code Detection
function isDuplicateCode(code: string) {
  if (!code) return false
  return contacts.value.filter(c => c.code === code).length > 1
}

function openAddDialog() {
  isEdit.value = false
  form.value = { type: 'user' }
  dialogVisible.value = true
}

function editContact(row: any) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function saveContact() {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  const formData = { ...form.value }

  // Check for duplicate code
  const isCodeExist = db.contacts.some((c: any) => c.code === formData.code && c.id !== formData.id)
  
  if (isEdit.value) {
    // Edit Mode: Strict Validation
    if (isCodeExist) {
      return ElMessage.error('代碼 (Code) 已被使用，請更換！')
    }
    const index = db.contacts.findIndex((c: any) => c.id === formData.id)
    if (index !== -1) db.contacts[index] = formData
  } else {
    // Add Mode: Soft Warning
    if (isCodeExist) {
      ElMessage.warning('注意：此代碼已存在，請確認是否重複')
    }
    // Check ID existence for Add Mode
    const isIdExist = db.contacts.some((c: any) => c.lineId === formData.lineId)
    if (isIdExist) {
       return ElMessage.error('此 LINE ID 已存在於通訊錄中')
    }

    formData.id = uuidv4()
    db.contacts.push(formData)
  }
  
  await (window as any).ipcRenderer.invoke('db:save', db)
  dialogVisible.value = false
  loadContacts()
  ElMessage.success('儲存成功')
}

async function deleteContact(id: string) {
  if (!confirm('確定要刪除此聯絡人嗎？')) return
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  db.contacts = db.contacts.filter((c: any) => c.id !== id)
  await (window as any).ipcRenderer.invoke('db:save', db)
  loadContacts()
  ElMessage.success('已刪除')
}

// GAS Import Logic
async function openImportDialog() {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  const url = db.settings.gasUrl
  if (!url) {
    return ElMessage.warning('請先設定 GAS URL')
  }
  
  importDialogVisible.value = true
  loadingGas.value = true
  try {
    const data = await (window as any).ipcRenderer.invoke('gas:fetch', url)
    const rawData = Array.isArray(data) ? data : []
    
    // Process data to identify updates vs new
    const processedData = rawData.map((item: any) => {
      const existing = db.contacts.find((c: any) => c.lineId === item.id)
      
      // If exact match (ID & Name), mark as 'skip'
      if (existing && existing.name === item.name) {
        return { ...item, status: 'skip' }
      }

      return {
        ...item,
        status: existing ? 'update' : 'new',
        existingData: existing
      }
    })

    // Filter out skipped items
    gasData.value = processedData.filter((item: any) => item.status !== 'skip')
    
    if (gasData.value.length === 0) {
      ElMessage.info('GAS 上沒有資料')
    }
  } catch (e: any) {
    ElMessage.error('匯入失敗: ' + e.message)
  } finally {
    loadingGas.value = false
  }
}

function handleSelectionChange(val: any[]) {
  selectedImport.value = val
}

async function confirmImport() {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  let addedCount = 0
  let updatedCount = 0

  for (const item of selectedImport.value) {
    if (item.status === 'update') {
      // Update Name only, keep Code
      const index = db.contacts.findIndex((c: any) => c.lineId === item.id)
      if (index !== -1) {
        db.contacts[index].name = item.name
        // db.contacts[index].code = item.name // DO NOT UPDATE CODE
        updatedCount++
      }
    } else {
      // New Contact
      db.contacts.push({
        id: uuidv4(),
        name: item.name,
        code: item.name, // Default code = name
        lineId: item.id,
        type: item.type || 'group'
      })
      addedCount++
    }
  }
  
  await (window as any).ipcRenderer.invoke('db:save', db)
  importDialogVisible.value = false
  loadContacts()
  ElMessage.success(`匯入完成：新增 ${addedCount} 筆，更新 ${updatedCount} 筆`)
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
  flex-shrink: 0; /* Prevent header from shrinking */
}

.page-title {
  margin: 0;
  font-family: 'Courier New', monospace;
  color: var(--tech-primary);
  letter-spacing: 2px;
}

.table-container {
  padding: 20px;
  flex: 1; /* Take remaining space */
  overflow: hidden; /* Required for el-table height calculation */
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* Ensure table takes full height of container */
:deep(.el-table) {
  flex: 1;
  height: 100% !important;
}

/* ... rest of styles ... */

.loading-container {
  text-align: center;
  padding: 40px;
}

.tech-loader {
  width: 40px;
  height: 40px;
  border: 2px solid var(--tech-primary);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

.loading-text {
  color: var(--tech-primary);
  letter-spacing: 2px;
  font-size: 12px;
  animation: blink 1s infinite;
}

.dialog-footer {
  margin-top: 20px;
  text-align: right;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes blink {
  50% { opacity: 0.5; }
}

.code-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.duplicate-tag :deep(.el-tag__content) {
  align-items: center;
  display: flex;
  gap: 4px;
}

/* Checkbox Styles */
:deep(.el-checkbox__input.is-checked .el-checkbox__inner),
:deep(.el-checkbox__input.is-indeterminate .el-checkbox__inner) {
  background-color: var(--tech-primary);
  border-color: var(--tech-primary);
}

:deep(.el-checkbox__input.is-checked + .el-checkbox__label) {
  color: var(--tech-primary);
}

:deep(.el-checkbox__inner:hover) {
  border-color: var(--tech-primary);
}

/* Tech Button Styles (Default Type) */
.tech-btn:not(.el-button--primary):not(.el-button--success):not(.el-button--danger) {
  background: transparent;
  border: 1px solid var(--tech-border);
  color: var(--tech-text-dim);
  transition: all 0.3s;
}

.tech-btn:not(.el-button--primary):not(.el-button--success):not(.el-button--danger):hover {
  border-color: var(--tech-primary);
  color: var(--tech-primary);
  background: rgba(6, 199, 85, 0.1); /* Slight tint */
}
</style>

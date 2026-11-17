<template>
  <div class="page-shell">
    <div class="page-toolbar">
      <div>
        <h2 class="page-heading">通訊錄與綁定管理</h2>
        <p class="page-subtitle">透過 LINE Webhook 自動註冊或手動維護發送對象，嚴格區分各 Bot 綁定權限</p>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="filterBotId" placeholder="依機器人篩選" clearable style="width: 180px" @change="loadData">
          <el-option v-for="b in bots" :key="b.id" :label="`${b.name} (${b.botCode})`" :value="b.id" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜尋名稱 / 代碼 / LINE ID" clearable style="width: 220px" />
        <el-button @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>重新整理</span>
        </el-button>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon>
          <span>新增聯絡人</span>
        </el-button>
      </div>
    </div>

    <div class="surface-card table-card">
      <el-table :data="filteredContacts" v-loading="loading" style="width: 100%">
        <el-table-column prop="name" label="顯示名稱" min-width="160" />
        <el-table-column label="呼叫代碼 (target_code)" min-width="170">
          <template #default="{ row }">
            <span v-if="row.code" class="mono-code">{{ row.code }}</span>
            <span v-else class="muted-text">未設定</span>
          </template>
        </el-table-column>
        <el-table-column label="類型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.contactType === 'group' ? 'warning' : 'info'" size="small" effect="light">
              {{ row.contactType === 'group' ? '群組' : '個人' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="LINE ID" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono-code">{{ row.lineId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已綁定機器人" min-width="200">
          <template #default="{ row }">
            <div class="tag-wrap">
              <el-tag v-for="bid in row.linkedBotIds" :key="bid" type="success" size="small" effect="plain">
                {{ getBotLabel(bid) }}
              </el-tag>
              <span v-if="!row.linkedBotIds || row.linkedBotIds.length === 0" class="muted-text">尚未綁定</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">編輯</el-button>
            <el-button size="small" type="danger" plain @click="deleteContact(row)">刪除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '編輯聯絡人' : '新增聯絡人'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="顯示名稱">
          <el-input v-model="form.name" placeholder="例如：IT 維運告警群" />
        </el-form-item>
        <el-form-item label="呼叫代碼">
          <el-input v-model="form.code" placeholder="唯一代碼，例如 IT_ALERT（選填）" />
        </el-form-item>
        <el-form-item label="類型">
          <el-select v-model="form.contactType" style="width: 100%">
            <el-option label="個人 (user)" value="user" />
            <el-option label="群組 (group)" value="group" />
          </el-select>
        </el-form-item>
        <el-form-item label="LINE ID">
          <el-input v-model="form.lineId" :disabled="isEdit" placeholder="U... 或 C..." />
        </el-form-item>
        <el-form-item label="綁定機器人">
          <el-select v-model="form.linkedBotIds" multiple placeholder="選擇可發送給此對象的 Bot" style="width: 100%">
            <el-option v-for="b in bots" :key="b.id" :label="`${b.name} (${b.botCode})`" :value="b.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveContact">儲存</el-button>
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

const contacts = ref<any[]>([])
const bots = ref<any[]>([])
const filterBotId = ref('')
const keyword = ref('')
const loading = ref(false)

const dialogVisible = ref(false)
useDialogEsc([dialogVisible])
const isEdit = ref(false)
const saving = ref(false)
const editingId = ref('')
const form = reactive({
  name: '',
  code: '',
  contactType: 'user',
  lineId: '',
  linkedBotIds: [] as string[],
})

const filteredContacts = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return contacts.value
  return contacts.value.filter((c) =>
    (c.name || '').toLowerCase().includes(kw)
    || (c.code || '').toLowerCase().includes(kw)
    || (c.lineId || '').toLowerCase().includes(kw)
  )
})

async function loadData() {
  loading.value = true
  try {
    const [botsRes, contactsRes] = await Promise.all([
      http.get('/admin/bots'),
      http.get('/admin/contacts', { params: { botId: filterBotId.value || undefined } }),
    ])
    bots.value = botsRes.data
    contacts.value = contactsRes.data
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  } finally {
    loading.value = false
  }
}

function getBotLabel(botId: string) {
  const found = bots.value.find((b) => b.id === botId)
  return found ? found.botCode : botId.slice(0, 8)
}

function openDialog(row?: any) {
  if (row) {
    isEdit.value = true
    editingId.value = row.id
    form.name = row.name
    form.code = row.code || ''
    form.contactType = row.contactType || 'user'
    form.lineId = row.lineId
    form.linkedBotIds = [...(row.linkedBotIds || [])]
  } else {
    isEdit.value = false
    editingId.value = ''
    form.name = ''
    form.code = ''
    form.contactType = 'user'
    form.lineId = ''
    form.linkedBotIds = []
  }
  dialogVisible.value = true
}

async function saveContact() {
  saving.value = true
  try {
    if (isEdit.value) {
      await http.put(`/admin/contacts/${editingId.value}`, form)
    } else {
      await http.post('/admin/contacts', form)
    }
    ElMessage.success('聯絡人已儲存')
    dialogVisible.value = false
    await loadData()
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  } finally {
    saving.value = false
  }
}

async function deleteContact(row: any) {
  try {
    await ElMessageBox.confirm(`確定要刪除聯絡人「${row.name}」嗎？`, '刪除確認', {
      type: 'warning',
      confirmButtonText: '刪除',
      cancelButtonText: '取消',
    })
    await http.delete(`/admin/contacts/${row.id}`)
    ElMessage.success('已刪除聯絡人')
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
  flex-wrap: wrap;
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
</style>

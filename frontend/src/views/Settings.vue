<template>
  <div class="page-shell">
    <div class="page-toolbar">
      <div>
        <h2 class="page-heading">LINE 機器人帳號設定</h2>
        <p class="page-subtitle">管理多組 LINE 官方帳號憑證，支援一鍵向 LINE API 驗證 Token 並同步 Bot ID</p>
      </div>
      <div class="toolbar-actions">
        <el-button @click="loadBots">
          <el-icon><Refresh /></el-icon>
          <span>重新整理</span>
        </el-button>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon>
          <span>新增機器人</span>
        </el-button>
      </div>
    </div>

    <!-- Webhook URL 對外設定提示卡 -->
    <div class="surface-card webhook-guide-card">
      <div class="guide-left">
        <div class="guide-title">對外 Webhook 網址規則</div>
        <div class="guide-desc">
          請將各機器人專屬的 Webhook URL 設定於 LINE Developers Console。
        </div>
      </div>
      <div class="guide-domain">
        <span class="domain-label">對外域名 / Port 預覽：</span>
        <el-input v-model="publicOrigin" size="small" style="width: 280px" />
      </div>
    </div>

    <div v-if="bots.length === 0 && !loading" class="surface-card empty-state">
      尚未設定任何 LINE 機器人帳號，請點擊右上角「新增機器人」。
    </div>

    <div class="bots-grid" v-loading="loading">
      <div v-for="bot in bots" :key="bot.id" class="surface-card bot-card">
        <div class="bot-card-top">
          <div>
            <div class="bot-name-row">
              <span class="bot-name">{{ bot.name }}</span>
              <el-tag :type="bot.enabled === 1 ? 'success' : 'info'" size="small" effect="light">
                {{ bot.enabled === 1 ? '啟用中' : '已停用' }}
              </el-tag>
            </div>
            <div class="bot-code-row">
              呼叫代碼：<span class="mono-code">{{ bot.botCode }}</span>
            </div>
          </div>
          <div class="bot-card-actions">
            <el-button size="small" :loading="syncingId === bot.id" @click="syncBotInfo(bot)">
              驗證並同步 ID
            </el-button>
            <el-button size="small" @click="openDialog(bot)">編輯</el-button>
            <el-button size="small" type="danger" plain @click="deleteBot(bot)">刪除</el-button>
          </div>
        </div>

        <div class="bot-meta-grid">
          <div class="meta-item">
            <div class="meta-label">Bot User ID (Webhook destination)</div>
            <div class="meta-val">{{ bot.lineBotUserId || '尚未同步（可點擊右上方同步）' }}</div>
          </div>
          <div class="meta-item">
            <div class="meta-label">Basic ID</div>
            <div class="meta-val">{{ bot.lineBasicId || '-' }}</div>
          </div>
          <div class="meta-item">
            <div class="meta-label">Channel Access Token</div>
            <div class="meta-val mono-text">{{ bot.channelAccessTokenMasked || '未設定' }}</div>
          </div>
          <div class="meta-item">
            <div class="meta-label">Channel Secret</div>
            <div class="meta-val mono-text">{{ bot.channelSecretMasked || '未設定' }}</div>
          </div>
        </div>

        <div class="webhook-box">
          <span class="webhook-label">Webhook URL</span>
          <code class="webhook-url">{{ `${publicOrigin}/line-webhook/${bot.botCode}` }}</code>
          <el-button link type="primary" size="small" @click="copyWebhookUrl(bot.botCode)">複製</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '編輯機器人設定' : '新增 LINE 機器人'" width="580px" destroy-on-close>
      <el-form :model="form" label-width="150px">
        <el-form-item label="機器人名稱">
          <el-input v-model="form.name" placeholder="例如：IT 報修通知機器人" />
        </el-form-item>
        <el-form-item label="呼叫代碼 (bot_code)">
          <el-input v-model="form.botCode" placeholder="例如：IT_BOT（用於 API 與 Webhook 路徑）" />
        </el-form-item>
        <el-form-item label="Bot User ID">
          <el-input v-model="form.lineBotUserId" placeholder="U 開頭字串，儲存後可一鍵同步自動填入" />
        </el-form-item>
        <el-form-item label="Channel Access Token">
          <el-input
            v-model="form.channelAccessToken"
            type="textarea"
            :rows="3"
            :placeholder="isEdit ? '留空表示保留原 Token 不修改' : '請貼上 LINE Messaging API Token'"
          />
        </el-form-item>
        <el-form-item label="Channel Secret">
          <el-input
            v-model="form.channelSecret"
            type="password"
            show-password
            :placeholder="isEdit ? '留空表示保留原 Secret 不修改' : '請貼上 Channel Secret（供 Webhook 驗簽）'"
          />
        </el-form-item>
        <el-form-item label="方案免費額度參考">
          <el-input-number v-model="form.quotaLimit" :min="0" :max="1000000" />
          <span class="hint-inline">台灣輕用量為 200 則/月</span>
        </el-form-item>
        <el-form-item label="啟用狀態">
          <el-switch v-model="form.enabledBool" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveBot">儲存設定</el-button>
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

const bots = ref<any[]>([])
const loading = ref(false)
const syncingId = ref('')
const publicOrigin = ref('https://ytcs.ytec.com.tw:1689')

const dialogVisible = ref(false)
useDialogEsc([dialogVisible])
const isEdit = ref(false)
const saving = ref(false)
const editingId = ref('')
const form = reactive({
  botCode: '',
  name: '',
  lineBotUserId: '',
  lineBasicId: '',
  channelAccessToken: '',
  channelSecret: '',
  quotaLimit: 200,
  enabledBool: true,
})

async function loadBots() {
  loading.value = true
  try {
    const res = await http.get('/admin/bots')
    bots.value = res.data
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  } finally {
    loading.value = false
  }
}

function openDialog(row?: any) {
  if (row) {
    isEdit.value = true
    editingId.value = row.id
    form.botCode = row.botCode
    form.name = row.name
    form.lineBotUserId = row.lineBotUserId || ''
    form.lineBasicId = row.lineBasicId || ''
    form.channelAccessToken = ''
    form.channelSecret = ''
    form.quotaLimit = row.quotaLimit ?? 200
    form.enabledBool = row.enabled === 1
  } else {
    isEdit.value = false
    editingId.value = ''
    form.botCode = ''
    form.name = ''
    form.lineBotUserId = ''
    form.lineBasicId = ''
    form.channelAccessToken = ''
    form.channelSecret = ''
    form.quotaLimit = 200
    form.enabledBool = true
  }
  dialogVisible.value = true
}

async function saveBot() {
  saving.value = true
  try {
    const payload = {
      botCode: form.botCode,
      name: form.name,
      lineBotUserId: form.lineBotUserId,
      lineBasicId: form.lineBasicId,
      channelAccessToken: form.channelAccessToken,
      channelSecret: form.channelSecret,
      quotaLimit: form.quotaLimit,
      enabled: form.enabledBool ? 1 : 0,
    }
    if (isEdit.value) {
      await http.put(`/admin/bots/${editingId.value}`, payload)
    } else {
      await http.post('/admin/bots', payload)
    }
    ElMessage.success('機器人設定已儲存')
    dialogVisible.value = false
    await loadBots()
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  } finally {
    saving.value = false
  }
}

async function syncBotInfo(bot: any) {
  syncingId.value = bot.id
  try {
    await http.post(`/admin/bots/${bot.id}/sync-info`)
    ElMessage.success(`已完成驗證並同步 ${bot.name} 資訊`)
    await loadBots()
  } catch (err) {
    ElMessage.error(extractErrorMessage(err))
  } finally {
    syncingId.value = ''
  }
}

async function deleteBot(bot: any) {
  try {
    await ElMessageBox.confirm(`確定要刪除「${bot.name}」嗎？這將同時解除聯絡人與此 Bot 的綁定關係。`, '刪除確認', {
      type: 'warning',
      confirmButtonText: '刪除',
      cancelButtonText: '取消',
    })
    await http.delete(`/admin/bots/${bot.id}`)
    ElMessage.success('已刪除機器人')
    await loadBots()
  } catch (err: any) {
    if (err !== 'cancel') ElMessage.error(extractErrorMessage(err))
  }
}

async function copyWebhookUrl(botCode: string) {
  await navigator.clipboard.writeText(`${publicOrigin.value}/line-webhook/${botCode}`)
  ElMessage.success('已複製 Webhook URL')
}

onMounted(loadBots)
</script>

<style scoped>
.toolbar-actions {
  display: flex;
  gap: 10px;
}

.webhook-guide-card {
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.guide-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--lb-text-primary);
}

.guide-desc {
  font-size: 12.5px;
  color: var(--lb-text-secondary);
  margin-top: 3px;
}

.guide-domain {
  display: flex;
  align-items: center;
  gap: 8px;
}

.domain-label {
  font-size: 12.5px;
  color: var(--lb-text-secondary);
}

.bots-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.bot-card {
  padding: 20px 22px;
}

.bot-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.bot-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bot-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--lb-text-primary);
}

.bot-code-row {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--lb-text-secondary);
}

.bot-card-actions {
  display: flex;
  gap: 8px;
}

.bot-meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  padding: 14px;
  background: var(--lb-bg-subtle);
  border-radius: 10px;
  border: 1px solid var(--lb-border-light);
}

.meta-label {
  font-size: 11.5px;
  color: var(--lb-text-muted);
  margin-bottom: 4px;
}

.meta-val {
  font-size: 13px;
  color: var(--lb-text-primary);
  word-break: break-all;
}

.mono-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.webhook-box {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12.5px;
}

.webhook-label {
  font-weight: 600;
  color: var(--lb-text-secondary);
}

.webhook-url {
  background: #f1f5f9;
  color: #0f172a;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid var(--lb-border);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.hint-inline {
  margin-left: 10px;
  font-size: 12px;
  color: var(--lb-text-muted);
}

.empty-state {
  padding: 40px;
  text-align: center;
  color: var(--lb-text-muted);
}
</style>

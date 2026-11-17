<template>
  <div class="login-backdrop">
    <div class="login-card surface-card">
      <div class="login-header">
        <img src="/LINE_New_App_Icon.png" alt="LINE" class="login-logo" />
        <h1>LINE Message Hub</h1>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="帳號">
          <el-input v-model="form.username" placeholder="請輸入帳號" size="large" autocomplete="username" />
        </el-form-item>

        <el-form-item label="密碼">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="請輸入密碼"
            size="large"
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon :closable="false" class="login-alert" />

        <div class="login-actions">
          <el-button type="primary" size="large" class="login-submit" :loading="loading" @click="handleLogin">
            登入
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http, { authStorage, extractErrorMessage } from '../api/http'

const router = useRouter()
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  username: '',
  password: '',
})

async function handleLogin() {
  if (!form.username || !form.password) {
    errorMsg.value = '請輸入帳號與密碼'
    return
  }

  loading.value = true
  errorMsg.value = ''
  try {
    const res = await http.post('/admin/auth/login', form)
    authStorage.setSession(res.data.token, res.data.username)
    router.push('/dashboard')
  } catch (err) {
    errorMsg.value = extractErrorMessage(err)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-backdrop {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: radial-gradient(circle at 20% 20%, rgba(6, 199, 85, 0.08), transparent 45%),
    radial-gradient(circle at 80% 80%, rgba(37, 99, 235, 0.06), transparent 45%),
    #f4f6f9;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 36px 32px 32px;
  box-shadow: var(--lb-shadow-md);
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-logo {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  margin-bottom: 12px;
}

.login-header h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--lb-text-primary);
}

.login-header p {
  margin: 6px 0 0;
  font-size: 13.5px;
  color: var(--lb-text-secondary);
}

.login-alert {
  margin-bottom: 16px;
}

.login-actions {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

.login-submit {
  width: 140px;
  font-weight: 600;
}
</style>

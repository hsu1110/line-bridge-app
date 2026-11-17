import { createRouter, createWebHashHistory } from 'vue-router'
import { authStorage } from '../api/http'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { public: true, title: '管理員登入' } },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '總覽儀表板' } },
  { path: '/templates', name: 'Templates', component: () => import('../views/Templates.vue'), meta: { title: '訊息模板與發送測試' } },
  { path: '/contacts', name: 'Contacts', component: () => import('../views/Contacts.vue'), meta: { title: '通訊錄與綁定管理' } },
  { path: '/clients', name: 'ApiClients', component: () => import('../views/ApiClients.vue'), meta: { title: '外部系統 API 金鑰' } },
  { path: '/webhooks', name: 'Webhooks', component: () => import('../views/Webhooks.vue'), meta: { title: 'Webhook 除錯紀錄' } },
  { path: '/logs', name: 'Logs', component: () => import('../views/Logs.vue'), meta: { title: '訊息發送紀錄' } },
  { path: '/settings', name: 'Settings', component: () => import('../views/Settings.vue'), meta: { title: 'LINE 機器人設定' } },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach((to) => {
  const hasToken = !!authStorage.getToken()
  if (!to.meta.public && !hasToken) {
    return '/login'
  }
  if (to.path === '/login' && hasToken) {
    return '/dashboard'
  }
  return true
})

export default router

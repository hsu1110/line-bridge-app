import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: () => import('../views/Dashboard.vue') },
  { path: '/contacts', component: () => import('../views/Contacts.vue') },
  { path: '/templates', component: () => import('../views/Templates.vue') },
  { path: '/logs', component: () => import('../views/Logs.vue') },
  { path: '/settings', component: () => import('../views/Settings.vue') },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

export default router

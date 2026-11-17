import axios from 'axios'

const TOKEN_KEY = 'lb_admin_token'
const USER_KEY = 'lb_admin_user'

export const authStorage = {
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY)
  },
  setSession(token: string, username: string) {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, username)
  },
  getUsername(): string {
    return localStorage.getItem(USER_KEY) || 'admin'
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 自動附加管理員 Bearer Token
http.interceptors.request.use((config) => {
  const token = authStorage.getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 遇到 401 登入逾期時清除狀態並導向登入頁
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !window.location.hash.includes('/login')) {
      authStorage.clear()
      window.location.hash = '#/login'
    }
    return Promise.reject(error)
  }
)

export function extractErrorMessage(error: any): string {
  return error?.response?.data?.error || error?.message || '操作失敗，請稍後再試'
}

export default http

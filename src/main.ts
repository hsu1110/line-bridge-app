import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './assets/theme.css'
import App from './App.vue'
import router from './router'


const app = createApp(App)

// Force Dark Mode
document.documentElement.classList.add('dark')

app.use(ElementPlus)
app.use(router)
app.mount('#app')

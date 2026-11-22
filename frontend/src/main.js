import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

// 全局样式（引入顺序：Element Plus 内置 → admin-theme.css → element-overrides.css）
import './styles/admin-theme.css'
import './styles/element-overrides.css'
// 小运操作确认卡片样式（`.xy-` 前缀，与旧后台 admin.html 共用同一份）
import './styles/xiaoyun-action.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 恢复登录态（刷新页面时 localStorage 有 token/user）
const auth = useAuthStore()
auth.restore()

// 全局错误处理：把 API 错误转成 ElMessage
app.config.errorHandler = (err) => {
  console.error('[Vue Error]', err)
}

app.mount('#app')

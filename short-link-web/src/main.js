import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 兜底：组件渲染/事件里的未捕获异常不让页面白屏
app.config.errorHandler = (err) => {
  console.error(err)
  ElMessage.error('页面出错了，请刷新重试')
}

// pinia 必须先于 router 安装，路由守卫里要用 store
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')

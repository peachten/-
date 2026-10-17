import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// pinia 必须先于 router 安装，路由守卫里要用 store
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')

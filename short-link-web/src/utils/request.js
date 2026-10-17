import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一请求封装
 * - baseURL 固定为 /api，由 Vite 代理转发到后端 8000
 * - 自动携带 Authorization（Sa-Token 的 token-name 也是 Authorization）
 * - 统一处理业务错误码与未登录
 */
const service = axios.create({
  baseURL: '/api',
  timeout: 10000
})

service.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== '0') {
      // A0006：后端返回未登录，清掉本地登录态并跳登录页
      if (res.code === 'A0006') {
        localStorage.removeItem('token')
        localStorage.removeItem('username')
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = '/login'
        }
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    // 业务成功时直接返回 data，调用方不用重复解包
    return res.data
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      window.location.href = '/login'
    }
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default service

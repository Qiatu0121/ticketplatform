import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearSession } from '../utils/user'

// axios 统一封装：
//  baseURL /api —— 开发由 Vite 代理到 8080，生产由 Nginx 反代
//  请求拦截器 —— 登录后自动带上 Authorization: Bearer <token>
//  响应拦截器 —— 自动拆解后端统一结构 { code, message, data }，code!=200 弹提示
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const body = res.data

    // 后端正常返回：{ code: 200, message: 'success', data }
    if (body && typeof body === 'object' && body.code === 200) {
      return body.data
    }

    const msg = body?.message || '请求失败'

    // token 失效 / 未登录：清掉本地登录态并回登录页（带上回跳地址）
    if (msg.includes('未登录') || msg.includes('登录已过期')) {
      clearSession()
      if (window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.href = `/login?redirect=${redirect}`
      }
    }

    // 轮询等场景可以传入 silent，避免重复弹提示
    if (!res.config?.silent) {
      ElMessage.error(msg)
    }
    return Promise.reject(new Error(msg))
  },
  (err) => {
    // HTTP 层错误（后端没启动、网关 502、网络断开……）
    const msg = err.response?.data?.message || '网络异常，请稍后再试'
    if (!err.config?.silent) {
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export default request

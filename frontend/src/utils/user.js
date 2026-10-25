// 登录态管理：用 Vue 响应式对象作为共享状态，登录/登出后导航栏等组件立即刷新
// （持久化到 localStorage，刷新不丢）
import { reactive } from 'vue'

const KEY = 'ticket_user'
const TOKEN_KEY = 'ticket_token'

const readUser = () => {
  try {
    return JSON.parse(localStorage.getItem(KEY))
  } catch {
    return null
  }
}

// 单一响应式数据源：App.vue 的导航、路由守卫都从它读
export const authState = reactive({
  user: readUser(),
  token: localStorage.getItem(TOKEN_KEY)
})

export const getUser = () => authState.user

export const setUser = (user) => {
  localStorage.setItem(KEY, JSON.stringify(user))
  authState.user = user
}

export const clearUser = () => {
  localStorage.removeItem(KEY)
  authState.user = null
}

export const getToken = () => authState.token

export const setToken = (token) => {
  localStorage.setItem(TOKEN_KEY, token)
  authState.token = token
}

export const clearToken = () => {
  localStorage.removeItem(TOKEN_KEY)
  authState.token = null
}

/** 登出：清掉 token + 用户信息 */
export const clearSession = () => {
  localStorage.removeItem(KEY)
  localStorage.removeItem(TOKEN_KEY)
  authState.user = null
  authState.token = null
}

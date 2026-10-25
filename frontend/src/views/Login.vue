<template>
  <div class="auth-page">
    <div class="particles" aria-hidden="true">
      <span
        v-for="(p, i) in particles"
        :key="i"
        class="particle"
        :style="{
          left: p.left + '%',
          fontSize: p.size + 'px',
          animationDuration: p.duration + 's',
          animationDelay: p.delay + 's',
          '--drift': p.drift + 'px'
        }"
      >{{ p.emoji }}</span>
    </div>
    <div class="auth-card">
      <div class="auth-left">
        <div>
          <div class="auth-left-logo">🎫</div>
          <h2>校园活动抢票平台</h2>
          <p class="auth-left-tagline">精彩活动，一触即抢</p>
          <ul class="auth-features">
            <li>📅 热门活动 · 实时开抢</li>
            <li>🎟️ 票种任选 · 一键下单</li>
            <li>⏰ 15 分钟 · 超时自动释放</li>
          </ul>
        </div>
      </div>
      <div class="auth-right">
        <h3 class="auth-title">欢迎回来 👋</h3>
        <p class="auth-sub">登录你的账号，继续抢票之旅</p>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名或手机号">
            <el-input
              v-model="account"
              size="large"
              placeholder="请输入用户名或手机号"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="password"
              type="password"
              size="large"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="primary-btn"
            native-type="submit"
            :loading="loading"
          >
            登 录
          </el-button>
        </el-form>
        <div class="auth-alt">还没有账号？<router-link to="/register">立即注册</router-link></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '../api/user'
import { setToken, setUser } from '../utils/user'
import { makeParticles } from '../utils/particles'

const route = useRoute()
const router = useRouter()
const account = ref('')
const password = ref('')
const loading = ref(false)
const particles = makeParticles()

async function submit() {
  if (loading.value) return
  if (!account.value || !password.value) {
    ElMessage.warning('请填写用户名/手机号和密码')
    return
  }

  loading.value = true
  try {
    const data = await login(account.value, password.value)
    setToken(data.token)
    setUser(data.user)
    ElMessage.success(`欢迎回来，${data.user.nickname || data.user.username}`)

    // 只允许跳回站内地址，避免 redirect 被利用做外跳
    const redirect = route.query.redirect
    const safeRedirect =
      typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
        ? redirect
        : ''
    // 管理员默认进入管理端，普通用户回首页或登录前访问的页面
    const fallback = data.user.role === 'ADMIN' ? '/admin/activity' : '/'
    router.replace(safeRedirect || fallback)
  } catch {
    // 请求拦截器已经弹过后端返回的错误提示
  } finally {
    loading.value = false
  }
}
</script>

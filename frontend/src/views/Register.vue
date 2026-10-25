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
          <div class="auth-left-logo">🎟️</div>
          <h2>校园活动抢票平台</h2>
          <p class="auth-left-tagline">加入我们，解锁精彩活动</p>
          <ul class="auth-features">
            <li>📅 热门活动 · 实时开抢</li>
            <li>🎟️ 票种任选 · 一键下单</li>
            <li>⏰ 15 分钟 · 超时自动释放</li>
          </ul>
        </div>
      </div>
      <div class="auth-right">
        <h3 class="auth-title">创建账号 🎉</h3>
        <p class="auth-sub">填写以下信息，开启你的抢票之旅</p>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名">
            <el-input v-model="username" size="large" placeholder="登录用户名" :prefix-icon="User" clearable />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="phone" size="large" placeholder="请输入手机号" :prefix-icon="Iphone" clearable />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="nickname" size="large" placeholder="请输入昵称" :prefix-icon="Avatar" clearable />
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
            注 册
          </el-button>
        </el-form>
        <div class="auth-alt">已有账号？<router-link to="/login">去登录</router-link></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Iphone, Avatar, Lock } from '@element-plus/icons-vue'
import { register } from '../api/user'
import { makeParticles } from '../utils/particles'

const router = useRouter()
const username = ref('')
const phone = ref('')
const nickname = ref('')
const password = ref('')
const loading = ref(false)
const particles = makeParticles()

async function submit() {
  if (loading.value) return
  if (!username.value || !phone.value || !nickname.value || !password.value) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (!/^\d{6,20}$/.test(phone.value)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }

  loading.value = true
  try {
    await register(username.value, phone.value, nickname.value, password.value)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch {
    // 请求拦截器已经弹过后端返回的错误提示（如用户名/手机号重复）
  } finally {
    loading.value = false
  }
}
</script>

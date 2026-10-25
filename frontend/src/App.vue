<template>
  <div>
    <header class="header">
      <div class="logo" @click="$router.push('/')">
        <span class="logo-icon">🎫</span>
        <span class="logo-text">校园活动抢票平台</span>
      </div>

      <nav class="nav">
        <router-link to="/">活动</router-link>
        <router-link v-if="user && user.role !== 'ADMIN'" to="/orders">我的订单</router-link>
        <template v-if="user && user.role === 'ADMIN'">
          <router-link to="/admin/activity">管理端</router-link>
          <router-link to="/admin/orders">核销</router-link>
        </template>
      </nav>

      <div class="actions">
        <template v-if="user">
          <div class="user">
            <span class="avatar">{{ avatarChar }}</span>
            <span class="nickname">{{ displayName }}</span>
            <button class="logout-btn" @click="logout">退出</button>
          </div>
        </template>
        <template v-else>
          <router-link to="/register" class="ghost-link">注册</router-link>
          <router-link to="/login" class="login-link">登录</router-link>
        </template>
      </div>
    </header>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { getUser, clearSession } from './utils/user'
import { logout as apiLogout } from './api/user'

const router = useRouter()
// computed 跟随共享响应式状态：登录/登出后导航栏立即更新，无需刷新页面
const user = computed(() => getUser())
const displayName = computed(() => user.value?.nickname || user.value?.username || '用户')
// 头像取昵称首字（按码点取，兼容 emoji 昵称）
const avatarChar = computed(() => Array.from(displayName.value)[0])

async function logout() {
  try {
    await apiLogout() // 让后端 token 失效
  } catch {
    // 后端失败也照常清本地登录态
  }
  clearSession()
  router.push('/login')
}
</script>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 0 24px;
  height: 56px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid #eef0f4;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
}
.logo-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #ff512f, #dd2476);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  box-shadow: 0 4px 10px rgba(221, 36, 118, 0.35);
}
.logo-text {
  font-size: 17px;
  font-weight: 700;
  color: #1f2328;
  white-space: nowrap;
}
.nav {
  display: flex;
  gap: 4px;
}
.nav a {
  text-decoration: none;
  color: #5f6672;
  font-size: 14px;
  font-weight: 500;
  padding: 7px 14px;
  border-radius: 8px;
  transition: color 0.2s, background-color 0.2s;
}
.nav a:hover {
  color: #dd2476;
  background: #fff0f4;
}
/* 当前页面：渐变胶囊高亮 */
.nav a.router-link-exact-active {
  color: #fff;
  background: linear-gradient(135deg, #ff512f, #dd2476);
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(221, 36, 118, 0.3);
}
.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 14px;
}
.user {
  display: flex;
  align-items: center;
  gap: 8px;
}
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff512f, #dd2476);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nickname {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}
.logout-btn {
  border: 1px solid #e0e3e8;
  background: #fff;
  color: #5f6672;
  font-size: 13px;
  padding: 5px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.logout-btn:hover {
  border-color: #dd2476;
  color: #dd2476;
}
.ghost-link {
  text-decoration: none;
  color: #5f6672;
  font-size: 14px;
  font-weight: 500;
  padding: 7px 12px;
  border-radius: 8px;
  transition: color 0.2s, background-color 0.2s;
}
.ghost-link:hover {
  color: #dd2476;
  background: #fff0f4;
}
.login-link {
  text-decoration: none;
  color: #fff;
  background: linear-gradient(135deg, #ff512f, #dd2476);
  font-size: 14px;
  font-weight: 600;
  padding: 8px 18px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(221, 36, 118, 0.3);
  transition: transform 0.2s, box-shadow 0.2s;
}
.login-link:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(221, 36, 118, 0.4);
}
.main {
  max-width: 1080px;
  margin: 0 auto;
  padding: 24px 16px;
}
@media (max-width: 720px) {
  .header {
    gap: 14px;
    padding: 0 14px;
  }
  .logo-text,
  .nickname {
    display: none;
  }
}
</style>

<template>
  <div v-loading="loading">
    <h2 class="page-title">最新活动</h2>

    <el-empty v-if="!loading && activities.length === 0" description="暂无活动，请耐心等待" />

    <el-row :gutter="16">
      <el-col v-for="a in activities" :key="a.id" :xs="24" :sm="12" :md="8" class="mb">
        <el-card shadow="hover" class="activity-card">
          <h3 class="activity-name">{{ a.name }}</h3>

          <p class="meta">🕐 {{ formatTime(a.startTime) || '时间待定' }}</p>
          <p class="meta">📍 {{ a.location || '地点待定' }}</p>
          <p class="detail">{{ a.detail || '暂无简介' }}</p>

          <div class="card-foot">
            <el-tag :type="statusType(a.status)">{{ statusText(a.status) }}</el-tag>
            <el-button type="primary" size="small" @click="goDetail(a.id)">查看详情</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listActivities } from '../api/activity'

const router = useRouter()
const activities = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    activities.value = await listActivities()
  } catch {
    // 请求拦截器已经弹过错误提示，这里保持空列表即可
  } finally {
    loading.value = false
  }
})

// 后端 LocalDateTime 序列化成 "2026-11-01T19:00:00"，这里只展示到分钟
const formatTime = (t) => (t ? t.replace('T', ' ').slice(0, 16) : '')
const statusText = (s) => ({ 0: '未开始', 1: '进行中', 2: '已结束' })[s] ?? '未知'
const statusType = (s) => ({ 0: 'info', 1: 'success', 2: 'warning' })[s] ?? 'info'
const goDetail = (id) => router.push(`/activity/${id}`)
</script>

<style scoped>
.page-title {
  margin-bottom: 16px;
}
.activity-card {
  border-radius: 12px;
}
.activity-name {
  margin-bottom: 8px;
  font-size: 17px;
}
.meta {
  margin: 4px 0;
  color: #909399;
  font-size: 13px;
}
.detail {
  margin-top: 8px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
</style>

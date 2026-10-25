<template>
  <div v-loading="loading">
    <template v-if="vo">
      <el-page-header class="mb" content="活动详情" @back="$router.back()" />

      <el-card class="mb">
        <div class="title-row">
          <h2>{{ vo.activity.name }}</h2>
          <el-tag :type="statusType(vo.activity.status)" size="large">
            {{ statusText(vo.activity.status) }}
          </el-tag>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="开始时间">
            {{ formatTime(vo.activity.startTime) || '待定' }}
          </el-descriptions-item>
          <el-descriptions-item label="地点">
            {{ vo.activity.location || '待定' }}
          </el-descriptions-item>
          <el-descriptions-item label="简介">
            {{ vo.activity.detail || '暂无简介' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-alert
        v-if="!activityActive"
        class="mb"
        :title="vo.activity.status === 2 ? '活动已结束，无法抢票' : '活动未开始，暂不可抢票'"
        type="warning"
        :closable="false"
        show-icon
      />

      <el-card>
        <template #header>
          <div class="card-header">
            <span>选择票种</span>
            <span class="header-right">
              <span class="tip">每个账号每场活动限购一次</span>
              <el-button link type="primary" size="small" :loading="refreshing" @click="refreshStocks">
                刷新余票
              </el-button>
            </span>
          </div>
        </template>

        <el-table :data="vo.tickets" border empty-text="该活动暂无票种">
          <el-table-column prop="name" label="票种" min-width="120" />
          <el-table-column label="价格" width="120">
            <template #default="{ row }">¥{{ formatPrice(row.price) }}</template>
          </el-table-column>
          <el-table-column prop="totalStock" label="总票数" width="100" />
          <el-table-column label="余票" width="100">
            <template #default="{ row }">
              <span :class="{ soldout: row.stock <= 0 }">{{ row.stock ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="140">
            <template #default="{ row }">
              <el-input-number
                v-model="row.qty"
                :min="1"
                :max="Math.max(1, row.stock ?? 0)"
                :disabled="row.stock <= 0 || !activityActive"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button
                type="danger"
                :disabled="row.stock <= 0 || !activityActive"
                :loading="grabbingId === row.id"
                @click="grab(row)"
              >
                {{ buttonText(row) }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-empty v-else-if="!loading" description="活动不存在或已下架" />
  </div>

  <el-dialog
    v-model="pendingVisible"
    title="订单创建中"
    width="420px"
    :close-on-click-modal="false"
    :show-close="false"
  >
    <div class="pending-info">
      <p>订单已提交，正在排队创建，请稍候...</p>
      <p class="hint">请求号：{{ pendingRequestId }}</p>
      <p class="hint">请保持当前页面，或稍后到「我的订单」查看。</p>
    </div>
  </el-dialog>

  <el-dialog v-model="successVisible" title="抢票成功 🎉" width="440px">
    <el-result icon="success" title="下单成功">
      <template #sub-title>
        <div class="success-info">
          <p>订单号：<b>{{ successOrderId }}</b></p>
          <p class="hint">
            订单已进入「待支付」状态，请在 15 分钟内完成支付；超时订单会自动取消并回补库存。
          </p>
        </div>
      </template>
      <template #extra>
        <el-button type="primary" @click="goOrders">去我的订单支付</el-button>
        <el-button @click="successVisible = false">继续浏览</el-button>
      </template>
    </el-result>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getActivityDetail } from '../api/activity'
import { getTicketDetail } from '../api/ticket'
import { submitAsyncOrder, getOrderRequest } from '../api/order'
import { getUser } from '../utils/user'

const route = useRoute()
const router = useRouter()
const vo = ref(null)
const loading = ref(true)
const refreshing = ref(false)
const grabbingId = ref(null)
const successVisible = ref(false)
const successOrderId = ref(null)
const pendingVisible = ref(false)
const pendingRequestId = ref('')
let unmounted = false

onBeforeUnmount(() => {
  unmounted = true
})

// 后端下单要求「活动状态 = 1（进行中）」
const activityActive = computed(() => vo.value?.activity?.status === 1)

onMounted(async () => {
  try {
    const data = await getActivityDetail(route.params.id)
    // 给每个票种单独挂一个购买数量，表格里直接绑定
    data.tickets = (data.tickets || []).map((t) => ({ ...t, qty: 1 }))
    vo.value = data
  } catch {
    // 请求拦截器已提示（例如活动不存在）
  } finally {
    loading.value = false
  }
})

const formatTime = (t) => (t ? t.replace('T', ' ').slice(0, 16) : '')
const formatPrice = (p) => Number(p ?? 0).toFixed(2)
const statusText = (s) => ({ 0: '未开始', 1: '进行中', 2: '已结束' })[s] ?? '未知'
const statusType = (s) => ({ 0: 'info', 1: 'success', 2: 'warning' })[s] ?? 'info'

// 活动详情走的是 Redis 缓存，余票可能不是最新；票种详情有独立缓存，可用来手动刷新
async function refreshStocks() {
  if (!vo.value) return
  refreshing.value = true
  try {
    const details = await Promise.all(
      vo.value.tickets.map((t) => getTicketDetail(t.id).catch(() => null))
    )
    details.forEach((d, i) => {
      if (!d) return
      const ticket = vo.value.tickets[i]
      ticket.stock = d.stock
      ticket.totalStock = d.totalStock
      ticket.price = d.price
    })
    ElMessage.success('余票已刷新')
  } finally {
    refreshing.value = false
  }
}

function buttonText(row) {
  if (row.stock <= 0) return '已抢完'
  if (vo.value?.activity?.status === 0) return '未开售'
  if (vo.value?.activity?.status === 2) return '已结束'
  return '立即抢票'
}

function goOrders() {
  successVisible.value = false
  router.push('/orders')
}

function newRequestId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID().replace(/-/g, '')
  }
  const time = Date.now().toString(16)
  const random = Math.random().toString(16).slice(2)
  return `${time}${random}`.padEnd(32, '0').slice(0, 32)
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function grab(ticket) {
  if (!getUser()) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  const count = ticket.qty || 1
  if (count > ticket.stock) {
    ElMessage.warning('购买数量超过余票')
    return
  }
  if (grabbingId.value) {
    return
  }

  const requestId = newRequestId()
  grabbingId.value = ticket.id
  pendingRequestId.value = requestId

  try {
    // 前端生成 requestId，HTTP 超时后仍可用同一个 requestId 查询状态
    const result = await submitAsyncOrder(ticket.id, count, requestId)

    if (result.status === 'SUCCESS') {
      handleSuccess(ticket, result.orderId, count)
      return
    }
    if (result.status === 'FAILED') {
      await handleFailure(ticket, result.message)
      return
    }

    pendingVisible.value = true
    await pollRequest(ticket, requestId, count)
  } catch {
    // 请求可能已被服务端接收但响应丢失，使用同一个 requestId 尝试恢复
    const recovered = await recoverRequest(requestId)
    if (recovered?.status === 'SUCCESS') {
      handleSuccess(ticket, recovered.orderId, count)
      return
    }
    if (recovered?.status === 'PENDING') {
      pendingVisible.value = true
      await pollRequest(ticket, requestId, count)
      return
    }
    if (recovered?.status === 'FAILED') {
      await handleFailure(ticket, recovered.message)
    }
    // 真正的提交失败，非 silent 请求拦截器已弹过提示
  } finally {
    pendingVisible.value = false
    grabbingId.value = null
  }
}

async function recoverRequest(requestId) {
  try {
    return await getOrderRequest(requestId)
  } catch {
    return null
  }
}

async function pollRequest(ticket, requestId, count) {
  // 轮询约 60 秒；服务端请求 TTL 为 180 秒，超时后仍可在订单页查看
  for (let i = 0; i < 75 && !unmounted; i++) {
    await sleep(800)
    try {
      const result = await getOrderRequest(requestId)
      if (result.status === 'SUCCESS') {
        handleSuccess(ticket, result.orderId, count)
        return
      }
      if (result.status === 'FAILED') {
        await handleFailure(ticket, result.message)
        return
      }
    } catch {
      // 轮询使用 silent，不弹提示；继续等待
    }
  }

  if (!unmounted) {
    ElMessage.warning('订单仍在处理中，请稍后在「我的订单」中查看')
    await refreshStocks()
  }
}

function handleSuccess(ticket, orderId, count) {
  successOrderId.value = orderId
  successVisible.value = true
  pendingVisible.value = false
  // 库存已在服务端预留成功，本地同步扣减
  ticket.stock = Math.max(0, ticket.stock - count)
}

async function handleFailure(ticket, message) {
  pendingVisible.value = false
  ElMessage.error(message || '订单创建失败')
  // 失败会回补服务端库存，重新拉取余票
  await refreshStocks()
}
</script>

<style scoped>
.detail {
  max-width: 720px;
  margin: 0 auto;
}
.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.title-row h2 {
  font-size: 22px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.tip {
  color: #909399;
  font-size: 13px;
}
.soldout {
  color: #f56c6c;
  font-weight: 600;
}
.success-info {
  line-height: 1.8;
}
.success-info .hint {
  color: #909399;
  font-size: 13px;
  margin-top: 6px;
}
.pending-info {
  line-height: 1.9;
  text-align: center;
}
.pending-info .hint {
  color: #909399;
  font-size: 13px;
}
</style>

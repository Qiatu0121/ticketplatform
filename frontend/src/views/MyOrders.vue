<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">我的订单</h2>
      <span class="tip">待支付订单 15 分钟内有效，超时自动取消并释放库存</span>
    </div>

    <el-table :data="orders" border v-loading="loading" empty-text="暂无订单">
      <el-table-column prop="orderNo" label="订单号" min-width="210" />
      <el-table-column label="金额" width="110">
        <template #default="{ row }">¥{{ formatPrice(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="数量" width="80">
        <template #default="{ row }">{{ row.count }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" min-width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'WAIT_PAY'"
            type="success"
            size="small"
            :loading="payingId === row.id"
            @click="pay(row)"
          >
            去支付
          </el-button>
          <span v-else-if="row.status === 'PAID'" class="muted">等待核销</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { myOrders, payOrder } from '../api/order'

const orders = ref([])
const loading = ref(true)
const payingId = ref(null)

onMounted(load)

async function load() {
  loading.value = true
  try {
    orders.value = await myOrders()
  } catch {
    // 请求拦截器已提示
  } finally {
    loading.value = false
  }
}

async function pay(row) {
  payingId.value = row.id
  try {
    await payOrder(row.id)
    ElMessage.success('支付成功')
    await load()
  } catch {
    // 请求拦截器已提示（订单超时取消、状态不允许支付等）
  } finally {
    payingId.value = null
  }
}

const formatTime = (t) => (t ? t.replace('T', ' ').slice(0, 16) : '')
const formatPrice = (p) => Number(p ?? 0).toFixed(2)
const statusText = (s) =>
  ({
    WAIT_PAY: '待支付',
    PAID: '已支付',
    CANCELED: '已取消',
    VERIFIED: '已核销',
    REFUNDED: '已退款'
  })[s] ?? s
const statusType = (s) =>
  ({
    WAIT_PAY: 'warning',
    PAID: 'success',
    CANCELED: 'info',
    VERIFIED: 'primary',
    REFUNDED: 'info'
  })[s] ?? 'info'
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
}
.tip {
  color: #909399;
  font-size: 13px;
}
.muted {
  color: #909399;
  font-size: 13px;
}
</style>

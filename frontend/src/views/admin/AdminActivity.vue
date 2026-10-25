<template>
  <div>
    <h2 class="page-title">管理端 · 活动与票种</h2>

    <el-card class="mb">
      <template #header>发布新活动</template>
      <el-form :model="form" label-width="80px">
        <el-form-item label="活动名称">
          <el-input v-model="form.name" placeholder="如：校园歌手大赛" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            placeholder="选择开始时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" placeholder="如：大学生活动中心" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.detail" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">未开始</el-radio>
            <el-radio :value="1">进行中</el-radio>
            <el-radio :value="2">已结束</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-button type="primary" :loading="submitting" @click="submitActivity">发布</el-button>
      </el-form>
    </el-card>

    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>已有活动</span>
          <el-button link type="primary" size="small" @click="load">刷新</el-button>
        </div>
      </template>

      <el-empty v-if="!loading && activities.length === 0" description="暂无活动" />

      <el-collapse v-model="active">
        <el-collapse-item v-for="a in activities" :key="a.id" :name="a.id">
          <template #title>
            <span class="activity-title">{{ a.name }}</span>
            <el-tag size="small" :type="statusType(a.status)">{{ statusText(a.status) }}</el-tag>
          </template>

          <el-table :data="a.tickets" size="small" border empty-text="暂无票种">
            <el-table-column prop="name" label="票种" />
            <el-table-column label="价格" width="120">
              <template #default="{ row }">¥{{ Number(row.price ?? 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="totalStock" label="总量" width="90" />
            <el-table-column prop="stock" label="余票" width="90" />
          </el-table>

          <el-button size="small" type="primary" class="mt" @click="openTicketDialog(a)">
            添加票种
          </el-button>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <el-dialog v-model="ticketDialog" :title="`添加票种 - ${current?.name ?? ''}`" width="420px">
      <el-form label-width="80px">
        <el-form-item label="票种名">
          <el-input v-model="ticketForm.name" placeholder="如：VIP 票" />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="ticketForm.price" :min="0" :precision="2" :step="10" />
        </el-form-item>
        <el-form-item label="总票数">
          <el-input-number v-model="ticketForm.totalStock" :min="1" :step="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ticketDialog = false">取消</el-button>
        <el-button type="primary" :loading="addingTicket" @click="submitTicket">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listActivities, getActivityDetail } from '../../api/activity'
import { createActivity, addTicket } from '../../api/admin'

const form = ref({ name: '', startTime: '', location: '', detail: '', status: 1 })
const activities = ref([])
const active = ref([])
const loading = ref(false)
const submitting = ref(false)

const ticketDialog = ref(false)
const current = ref(null)
const ticketForm = ref({ name: '', price: 0, totalStock: 100 })
const addingTicket = ref(false)

onMounted(load)

// 活动列表 + 每个活动的票种
async function load() {
  loading.value = true
  try {
    const list = await listActivities()
    activities.value = await Promise.all(
      list.map(async (activity) => {
        try {
          const detail = await getActivityDetail(activity.id)
          return { ...activity, tickets: detail?.tickets || [] }
        } catch {
          return { ...activity, tickets: [] }
        }
      })
    )
  } finally {
    loading.value = false
  }
}

async function submitActivity() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写活动名称')
    return
  }
  submitting.value = true
  try {
    await createActivity(form.value)
    ElMessage.success('发布成功')
    form.value = { name: '', startTime: '', location: '', detail: '', status: 1 }
    await load()
  } catch {
    // 请求拦截器已提示
  } finally {
    submitting.value = false
  }
}

function openTicketDialog(activity) {
  current.value = activity
  ticketForm.value = { name: '', price: 0, totalStock: 100 }
  ticketDialog.value = true
}

async function submitTicket() {
  if (!ticketForm.value.name.trim()) {
    ElMessage.warning('请填写票种名称')
    return
  }
  addingTicket.value = true
  try {
    await addTicket(current.value.id, ticketForm.value)
    ElMessage.success('添加成功')
    ticketDialog.value = false
    await load()
  } catch {
    // 请求拦截器已提示
  } finally {
    addingTicket.value = false
  }
}

const statusText = (s) => ({ 0: '未开始', 1: '进行中', 2: '已结束' })[s] ?? '未知'
const statusType = (s) => ({ 0: 'info', 1: 'success', 2: 'warning' })[s] ?? 'info'
</script>

<style scoped>
.page-title {
  margin-bottom: 16px;
  font-size: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.activity-title {
  margin-right: 10px;
  font-weight: 600;
}
</style>

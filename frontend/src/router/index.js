import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken, getUser } from '../utils/user'
import ActivityList from '../views/ActivityList.vue'
import ActivityDetail from '../views/ActivityDetail.vue'
import MyOrders from '../views/MyOrders.vue'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import AdminActivity from '../views/admin/AdminActivity.vue'
import AdminOrders from '../views/admin/AdminOrders.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: ActivityList },
    { path: '/activity/:id', name: 'activity-detail', component: ActivityDetail },
    {
      path: '/orders',
      name: 'orders',
      component: MyOrders,
      meta: { requiresAuth: true }
    },
    { path: '/login', name: 'login', component: Login },
    { path: '/register', name: 'register', component: Register },
    {
      path: '/admin/activity',
      name: 'admin-activity',
      component: AdminActivity,
      meta: { requiresAuth: true, adminOnly: true }
    },
    {
      path: '/admin/orders',
      name: 'admin-orders',
      component: AdminOrders,
      meta: { requiresAuth: true, adminOnly: true }
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

// 需要登录的页面检查 token，管理端页面额外检查 ADMIN 角色
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.meta.adminOnly) {
    const user = getUser()
    if (!user || user.role !== 'ADMIN') {
      ElMessage.error('无权限，仅管理员可访问')
      return { path: '/' }
    }
  }

  return true
})

export default router

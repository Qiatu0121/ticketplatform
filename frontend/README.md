# 校园活动抢票平台 · 前端

Vue 3 + Vite + Element Plus，对应后端 `ticketplatform` 的当前接口。

## 启动

```bash
# 1. 启动 MySQL、Redis、RabbitMQ，并按根 README 配置后端环境变量
#    或 backend/application-local.yml

# 2. 启动后端
#    ticketplatform/backend 目录下：mvn spring-boot:run

# 3. 启动前端
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173 。Vite 会把 `/api` 代理到 `http://localhost:8080`。

生产构建：`npm run build`，产物在 `dist/`，由 Nginx 托管并反代 `/api`。

## 页面

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/` | 活动列表 | 所有已发布活动 |
| `/activity/:id` | 活动详情与抢票 | 选择票种和数量，创建订单 |
| `/orders` | 我的订单 | 查看自己的订单并模拟支付 |
| `/login` | 登录 | 用户名或手机号登录 |
| `/register` | 注册 | 注册普通用户 |
| `/admin/activity` | 管理端 · 活动与票种 | 仅 ADMIN：发布活动、添加票种 |
| `/admin/orders` | 管理端 · 订单核销 | 仅 ADMIN：查询已支付订单并核销 |

## 管理员账号

项目不提供默认管理员密码。

首次启动时，只有满足以下条件才会创建管理员：

- 管理员用户不存在；
- 已配置 `ROOT_ADMIN_PASSWORD`，且长度至少 8 位。

用户名由 `ROOT_ADMIN_USERNAME` 决定，默认 `root`。

普通用户在注册页创建。管理端页面只对 `role = ADMIN` 的用户开放。

## 订单流程

前端默认走 RabbitMQ 异步下单：

1. 点击「立即抢票」→ 前端生成 `requestId` → 调用 `/api/order/async-create`。
2. 后端先预占库存并写入请求记录，返回 `PENDING`。
3. 页面显示「订单创建中」，每秒轮询 `/api/order/request/{requestId}`。
4. `SUCCESS`：展示订单号，订单进入「待支付」。
5. `FAILED`：显示失败原因并刷新余票。
6. 超过轮询时间仍为 `PENDING`：提示稍后在「我的订单」查看。

后续支付和核销流程不变：

- 在「我的订单」点击「去支付」→ 订单变为「已支付」。
- 管理员在「核销」页面点击「核销」→ 订单变为「已核销」。
- 待支付订单超过 15 分钟由后端定时任务自动取消，并回补数据库和 Redis 库存。

## 后端准备

后端启动时会执行以下初始化：

- 在配置了 `ROOT_ADMIN_PASSWORD` 且管理员不存在时创建管理员。
- 以数据库为准重建 Redis 库存和购买标记（单实例部署，启动完成后即可访问）。

新建票种时后端会自动写入 Redis 库存。活动状态为 `1`（进行中）时才允许下单；
发布活动时可在管理端表单中选择状态。

后端异步下单依赖 RabbitMQ。MQ 不可用时，请求会保持 `PENDING`，超过 TTL 后自动失败并回补库存。

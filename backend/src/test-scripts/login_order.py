import os
import threading
from collections import Counter
import requests

# 批量登录后在同一时刻并发调用抢票接口，统计成功与失败原因
base = "http://localhost:8080"
N = 10
TICKET_ID = int(os.environ.get("TEST_TICKET_ID", "2"))
tokens = []

PASSWORD = os.environ.get("TEST_USER_PASSWORD")
if not PASSWORD:
    raise SystemExit("请先设置 TEST_USER_PASSWORD 环境变量")

# 登录并收集 token
for i in range(1, N + 1):
    username = f"perf{i}"
    try:
        resp = requests.post(f"{base}/api/user/login",
                             data={"account": username, "password": PASSWORD},
                             timeout=5)
        j = resp.json()
        if j.get("code") != 200:
            print(f"{username} 登录失败：{j.get('message')}")
            continue
        token = j["data"]["token"]
        tokens.append(token)
        print(f"{username} 登录成功，token: {token[:12]}...")
    except Exception as e:
        print(f"{username} 登录异常: {e}")

print(f"共获取 {len(tokens)} 个 token")

if not tokens:
    raise SystemExit("没有 token，请检查服务是否启动、perf 用户是否存在")

# 所有线程到齐后同时发起抢票请求
barrier = threading.Barrier(len(tokens))
results = [None] * len(tokens)


def grab_ticket(idx, token):
    try:
        barrier.wait(timeout=10)
    except threading.BrokenBarrierError:
        pass
    try:
        resp = requests.post(
            f"{base}/api/order/create",
            headers={"Authorization": f"Bearer {token}"},
            data={"ticketId": TICKET_ID, "count": 1},
            timeout=10,
        )
        results[idx] = resp.json()
    except Exception as e:
        results[idx] = {"code": -1, "message": str(e)}


threads = [threading.Thread(target=grab_ticket, args=(i, t))
           for i, t in enumerate(tokens)]
for th in threads:
    th.start()
for th in threads:
    th.join()

# 汇总结果
ok = sum(1 for r in results if r and r.get("code") == 200)
print(f"总请求 {len(results)}，成功 {ok}，失败 {len(results) - ok}")

if ok < len(results):
    print("失败原因：")
    counter = Counter((r or {}).get("message", "未知")
                      for r in results if not r or r.get("code") != 200)
    for msg, cnt in counter.most_common():
        print(f"  {msg} × {cnt}")

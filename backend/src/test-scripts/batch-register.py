import os

import requests

# 批量注册压测用户；用户名或手机号已存在时注册会失败，可忽略
base = "http://localhost:8080"
N = 10

PASSWORD = os.environ.get("TEST_USER_PASSWORD")
if not PASSWORD:
    raise SystemExit("请先设置 TEST_USER_PASSWORD 环境变量")

for i in range(1, N + 1):
    username = f"perf{i}"
    phone = f"139002{i:05d}"
    nickname = f"p{i}"
    password = PASSWORD

    url = f"{base}/api/user/register"
    data = {
        "username": username,
        "phone": phone,
        "nickname": nickname,
        "password": password
    }
    try:
        resp = requests.post(url, data=data, timeout=3)
        print(f"[{i}] username:{username} finish, code={resp.status_code}")
    except Exception as e:
        print(f"[{i}] 注册异常: {e}")

print(f"已尝试注册 {N} 个用户")

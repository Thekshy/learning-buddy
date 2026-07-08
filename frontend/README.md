# Learning Buddy · Frontend

> Next.js 14 (App Router) + TypeScript + Tailwind

## 本地启动

```bash
npm install
cp .env.example .env.local   # 默认即可
npm run dev
# 访问 http://localhost:3000
```

## 目录

```
src/
├── app/
│   ├── layout.tsx        # 根布局
│   ├── globals.css       # Tailwind + 自定义
│   ├── page.tsx          # 登录/注册
│   └── chat/page.tsx     # 聊天主界面(Agent 调用链可视化)
```

## 与后端的连接

- 开发期通过 `next.config.js` 的 `rewrites` 把 `/api/*` 反代到 `http://localhost:8080`
- Token 存 `localStorage.lb_token`,每次请求 `Authorization: Bearer ...`

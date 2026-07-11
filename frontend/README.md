# Learning Buddy · Frontend

> Next.js 15 (App Router) + React 19 + TypeScript + Tailwind

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
│   ├── layout.tsx            # 根布局
│   ├── globals.css           # Tailwind + 自定义
│   ├── page.tsx              # 登录/注册
│   └── chat/page.tsx         # 聊天主界面(Agent 调用链可视化)
├── components/
│   ├── chat/                 # 聊天页拆出的子组件
│   │   ├── MessageBubble.tsx
│   │   ├── AgentCallCard.tsx
│   │   └── AgentChainStepper.tsx
│   ├── lib/utils.ts          # cn() 类名合并工具
│   └── ui/                   # react-bits 视觉组件
└── lib/
    ├── types.ts              # 前后端共享类型
    ├── auth.ts               # localStorage 鉴权收口
    └── api.ts                # 统一请求层(ApiError + authApi/chatApi)
```

## 与后端的连接

- 开发期通过 `next.config.js` 的 `rewrites` 把 `/api/*` 反代到 `http://localhost:8080`
- Token 存 `localStorage.lb_token`,每次请求 `Authorization: Bearer ...`

# Learning Buddy · 智能学习伙伴(多智能体)

> 短学期课程设计 · 多智能体 Web 应用 · 6 天交付
> **核心亮点**:不是套壳 Chatbot,而是一个 **Orchestrator + 5 个专职 Agent** 协作的真实多智能体系统,Agent 调用链可在前端可视化。

## ✨ 做了哪些事(多智能体怎么体现)

| Agent | 职责 | 触发场景 |
|---|---|---|
| **Orchestrator**(协调) | 意图识别 → 分发 → 汇总 → 记录调用链 | 每次请求入口 |
| **Planner**(路径规划) | 根据目标知识点 + 掌握度,生成阶段化学习计划 | "我想学 X,我是 Y 水平" |
| **Quiz**(出题判卷) | 动态出题(选择/填空/简答/编程)+ 自动判卷 + 解析 | 计划生成后 / 用户请求练习 |
| **Tutor**(答疑) | 知识讲解、举例、可追问;支持 RAG 检索增强 | 任意时刻提问 |
| **Recommender**(资源推荐) | 根据当前学习阶段推荐教程/文档/视频/项目 | 计划生成后 |
| **Reviewer**(复盘) | 判分 → 错题归集 → 更新掌握度 → 反馈给 Planner | 每次提交答案后 |

调用链会落库,前端可视化,演示时一眼能看出"多 Agent 协作"。

## 🧰 技术栈

| 层 | 选型 | 理由 |
|---|---|---|
| 后端 | **Java 21 + Spring Boot 3.3** | 用户指定;生态成熟,部署简单 |
| LLM | **MiniMax M3**(OpenAI 兼容协议) | 用户指定;Spring AI 直接对接 |
| 多 Agent 编排 | **自研 Orchestrator 状态机**(基于 Spring AI + 状态上下文) | LangGraph 是 Python 专属,Java 侧用状态机显式表达更直观;且状态可序列化(便于调用链可视化) |
| 关系库 | **H2 文件库(默认)/ MySQL / PostgreSQL 任选** | 设计全部表,但**实体类按需生成**——用到才建 |
| 向量库 | **zvec**(阿里开源) | 用户指定;本地嵌入式,零部署 |
| RAG | PDF/TXT/MD 切片 → 嵌入 → zvec 检索 → 注入 Tutor prompt | |
| 前端 | **Next.js 14 + TypeScript + Tailwind + shadcn/ui** | 现代感,RSC 提速,组件可复制 |
| 鉴权 | **JWT + bcrypt** | 无状态,够用 |

## 📁 目录结构

```
learning-buddy/
├── README.md
├── .env.example
├── .gitignore
├── backend/                     # Spring Boot 工程
│   ├── pom.xml
│   ├── src/main/java/com/learningbuddy/
│   │   ├── LearningBuddyApplication.java
│   │   ├── api/                 # 路由(Controller)
│   │   ├── agents/              # Orchestrator + Planner/Quiz/Tutor/Recommender/Reviewer
│   │   ├── graph/               # 状态机(Agent 调度图)
│   │   ├── core/                # LLM 客户端、配置、安全
│   │   ├── rag/                 # zvec 集成 + 检索增强
│   │   ├── config/              # Spring 配置类
│   │   ├── models/              # JPA 实体(按需生成)
│   │   ├── repositories/        # JPA Repository(按需生成)
│   │   ├── services/            # 业务服务
│   │   ├── dto/                 # DTO
│   │   └── security/            # JWT 过滤器等
│   ├── src/main/resources/application.yml
│   └── src/test/java/...
├── frontend/                    # Next.js 14 工程
├── docs/
│   ├── architecture.md          # 架构图 + 模块依赖
│   ├── db_schema.sql            # 完整关系库 schema(交付用)
│   └── demo-script.md           # 演示录屏脚本
├── scripts/                     # 启动 / 种子数据脚本
└── data/                        # 本地数据(不进 git)
    ├── zvec/                    # zvec 持久化目录
    └── learning-buddy.*.db      # H2 文件库
```

## 🚀 本地启动(干净机器)

### 0. 准备
- JDK 21+(`brew install openjdk@21`)
- Maven 3.9+(`brew install maven`)
- Node 20+(自带)
- 一个 MiniMax M3 API Key(`.env` 里填)

### 1. 克隆与配置
```bash
git clone <your-repo-url> learning-buddy
cd learning-buddy
cp .env.example .env
# 编辑 .env,填入 LLM_API_KEY 等真实值
```

### 2. 启动后端
```bash
cd backend
mvn spring-boot:run
# 健康检查:curl http://localhost:8080/api/health
```

### 3. 启动前端
```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:3000
```

### 4. (可选)重置关系库
H2 首次跑自动建表。如要重置:
```bash
rm -rf data/*.db
```

完整 schema 见 [`docs/db_schema.sql`](docs/db_schema.sql)(交付时使用)。

## 🗓 6 天开发路线

| Day | 目标 | 关键交付 |
|---|---|---|
| D1 | 骨架 + 鉴权 + LLM 接入 | 当前阶段 |
| D2 | Orchestrator + Planner + Quiz 跑通最小多 Agent 链路 | |
| D3 | Tutor + Recommender + 前端聊天 UI + Agent 调用链可视化 | |
| D4 | Reviewer + 进度面板 + 雷达图 | |
| D5 | RAG(zvec)+ 错题再练 + 学习报告 | |
| D6 | 联调 + 录屏 + 报告 + SQL 脚本 | |

## 🔐 约束(任务书要求)

- ✅ 仓库公开(GitHub/Gitee 任选)
- ✅ `.env` 不入库(`.gitignore` 已加)
- ✅ API Key 仅走环境变量
- ✅ 交付 `docs/db_schema.sql`
- ✅ 一键跑通(本 README 步骤)

## 📜 License

MIT — 仅用于课程作业演示。

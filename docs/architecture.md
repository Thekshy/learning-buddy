# Learning Buddy · 架构设计

> 课程设计专用架构文档,配合 README 使用。

## 一、整体拓扑

```
                ┌────────────────────────────────────┐
                │       用户浏览器 (Next.js 14)        │
                │  - 登录/注册                         │
                │  - 聊天主界面(展示当前 Agent)        │
                │  - Agent 调用链时间线                │
                │  - 学习路径 / 错题本 / 雷达图         │
                └───────────────┬────────────────────┘
                                │  HTTP/JSON + SSE
                                ▼
                ┌────────────────────────────────────┐
                │         FastAPI / Spring Boot        │
                │  - JWT 鉴权 (Spring Security)        │
                │  - REST 路由 (Controllers)           │
                │  - SSE 流式响应 (聊天)               │
                └───────────────┬────────────────────┘
                                │
                                ▼
        ┌────────────────────────────────────────────────────┐
        │             Orchestrator(协调 Agent)               │
        │   · 意图分类 → Agent 选择 → 串/并调度 → 结果汇总    │
        │   · 写入 agent_call_log(供前端可视化)              │
        └─┬──────────┬──────────┬──────────┬──────────┬─────┘
          │          │          │          │          │
          ▼          ▼          ▼          ▼          ▼
     ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
     │Planner │ │  Quiz  │ │ Tutor  │ │Recomm. │ │Reviewer│
     └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘
         │          │          │          │          │
         └──────────┴──────────┴─────┬────┴──────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              ▼                      ▼                      ▼
    ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
    │ LlmClient        │   │ RAG Pipeline     │   │ JPA Repositories │
    │ (Spring AI)      │   │  - Ingestor      │   │  (按需生成的实体)  │
    │  - chat          │   │  - Retriever     │   └────────┬─────────┘
    │  - struct-IO     │   │  - zvec 集成     │            │
    │  - embed         │   └────────┬─────────┘            ▼
    │  - retry/mock    │            │              ┌──────────────┐
    └────────┬─────────┘            │              │ H2/MySQL/PG  │
             │                      ▼              └──────────────┘
             │              ┌──────────────┐
             └─────────────►│   zvec        │
                            │  (本地嵌入式)  │
                            └──────────────┘
```

## 二、模块依赖

```
controllers
  └── services
        ├── agents (Orchestrator + 5 Agents)
        │     ├── LlmClient
        │     └── RAG (Retriever)
        ├── repositories
        └── entities (JPA)
```

- **api** → **services** → **agents** + **repositories** + **rag**
- **agents** 单向依赖 **core** (LlmClient),**rag** 单向依赖 **core** + **repositories**
- **graph** = Orchestrator 的状态机实现,内含调用链日志

## 三、多智能体如何"可见"

> 这部分最关键,直接对应评分点。

每次用户请求 → Orchestrator 生成一个 `request_id`,每个 Agent 调度产出一条 `agent_call_log`:

```
request_id: req-abc-123
├── Orchestrator  (started 00ms, finished 50ms, status=SUCCESS)
│   ├── Planner   (started 50ms, finished 1200ms, status=SUCCESS)
│   ├── Quiz      (started 50ms, finished 1500ms, status=SUCCESS)
│   └── Recommender (started 1300ms, finished 1900ms, status=SUCCESS)
```

前端用一次 `GET /api/agent-calls?request_id=req-abc-123` 拿到整棵树,用时间线组件渲染。**演示时能直接讲出"这条请求调了几个 Agent、各自干了多久、谁调了谁"**。

## 四、为什么不用 LangGraph(原 Python 方案)

- LangGraph 仅 Python,本项目后端是 Java。
- Java 侧没有成熟度对等的替代(且把 Python 拼回去会引入跨语言边界)。
- **自研 Orchestrator 状态机**反而更直观:
  - 用 `AgentContext` 显式表达一次请求的所有中间状态
  - 调度顺序由代码直接控制(无需 DSL)
  - 每步完成即写 `agent_call_log`,天然产生可视化数据
  - 后续可演进:若想图状编排,把 `Orchestrator` 替换为 `StateMachine` 即可

## 五、关键技术选型理由

| 决策 | 选项 | 理由 |
|---|---|---|
| Java 版本 | 21 | Spring Boot 3.3 官方推荐;虚拟线程预览可后续启用 |
| Spring Boot | 3.3.x | 长期支持;Spring AI 1.0 已稳定 |
| Spring AI | 1.0+ | OpenAI 兼容协议,接 MiniMax M3 一行 base-url 切换;结构化输出(BeanOutputConverter)是 Agent JSON 输出的基础 |
| 向量库 | zvec | 用户指定;本地嵌入式,零部署;阿里开源 |
| 关系库(开发) | H2 文件库 | 零配置;生产可切 MySQL/PG |
| ORM | Spring Data JPA + Hibernate | 实体按需生成,避免空架子 |
| 鉴权 | Spring Security + JWT | 无状态,适合前后端分离 |
| 前端构建 | Next.js 14 App Router | RSC 提速;TypeScript 端到端类型 |
| UI 库 | shadcn/ui + Tailwind | 现代感;不锁版本 |
| 图表 | Recharts | 雷达图/折线图开箱即用 |

## 六、数据流(典型场景)

### 场景 1:用户首次进入「我想学 Python 装饰器」

```
1. POST /api/chat {message: "..."}
2. Orchestrator.intent() → INTENT_LEARN (LLM 分类)
3. Orchestrator.dispatch():
     a. Planner.run() → 生成 3 阶段学习计划,写 plan_json
     b. Quiz.run(plan) → 出 3 道预测试题
     c. Recommender.run(plan) → 推荐 5 个资源
4. Orchestrator.aggregate() → {plan, quiz, resources}
5. 前端展示三块卡片,同时 GET /api/agent-calls?request_id=...
```

### 场景 2:用户提交答案

```
1. POST /api/attempts {quizId, answers[]}
2. Quiz.grade() → 判分 + 解析
3. Reviewer.run() → 更新 knowledge_progress + wrong_book
4. (异步) Planner 读取最新 progress,生成"下一步建议"写入 plan
5. 返回 {grade, progressDelta, nextPlan}
```

### 场景 3:用户上传 PDF,Tutor 基于资料答疑

```
1. POST /api/rag/upload (multipart)
2. DocumentIngestor: PDF → 切片(600 字/80 重叠)→ 嵌入 → zvec
3. POST /api/chat {message: "...", useRag: true}
4. Tutor.run():
     a. Retriever.retrieve(query, topK=5) → zvec 命中
     b. 拼 prompt: {context: 命中片段} + {question}
     c. LLM 回答,引用片段标号
5. 返回 {answer, references: [{docId, chunkId, score}]}
```

## 七、扩展点

- **新 Agent**:实现 `BaseAgent` 接口,在 `Orchestrator` 注册 intent 映射
- **新学科**:在 `subject` 表插一条 + 知识树 JSON
- **新 LLM 厂商**:Spring AI 已支持多家;`.env` 切 base-url 即可
- **图状编排**:把 `Orchestrator` 内部从 `if/switch` 升级为显式 DAG 即可,接口不变
- **离线模式**:`LLM_FALLBACK_MOCK=true` 时,所有 Agent 返回 mock 但仍走完整调用链(便于演示)

## 八、安全

- API Key 仅在 `.env`,不入库
- 密码 bcrypt(rounds=10,生产建议 12)
- JWT HS256,TTL 72h,生产建议接 Redis 黑名单
- 上传文件大小限制(RAG_MAX_UPLOAD_MB)
- CORS 仅放行 `FRONTEND_ORIGIN`
- 默认拒绝请求体过大的接口(防 DoS)

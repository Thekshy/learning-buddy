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
        │   · 把 5 个 @Tool 注册给 LLM(Spring AI FC)         │
        │   · LLM 自主决定调哪个/调几个工具 → 一次完成        │
        │   · 记录 tool 调用到 agent_call_log(供前端可视化)  │
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
controllers (api)
  ├── agents (Orchestrator —— Function Calling 编排)
  ├── services (SessionService 会话记忆 / QuizService 闭环判分)
  └── graph (AgentCallRecorder + LoggingToolCallingManager + CallContext)

agents
  ├── tools (LearningTools —— 5 个 @Tool 方法)
  │     ├── core (LlmClient + UserContext + QuizResultHolder)
  │     ├── rag (Retriever / DocumentIngestor)
  │     └── services (QuizService —— 出题落库 + 判分)
  └── graph (调用链记录,由 LoggingToolCallingManager 自动驱动)

services + tools
  └── repositories (9 个 JPA Repository)
        └── models (9 个 JPA 实体)
```

- **api** → **agents** + **services** + **rag** + **security**
- **agents**(Orchestrator)依赖 **tools**(LearningTools)、**graph**(AgentCallRecorder + CallContext)
- **tools** 依赖 **core**(LlmClient)、**rag**(Retriever)、**services**(QuizService)
- **services** 依赖 **repositories** → **models**(JPA 实体,9 张表)
- **rag** 单向依赖 **core**(LlmClient 做 embed)
- **graph**(LoggingToolCallingManager)通过 Spring AI 装配覆盖默认 ToolCallingManager,自动拦截所有工具调用

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
- **采用 Spring AI Function Calling**,让 LLM 自己决定调哪个/调几个工具,比硬编码 intent 路由更灵活:
  - 用 `AgentContext` 显式表达一次请求的所有中间状态(requestId / slots / data)
  - 工具调用由 LLM 自主选择,复合需求(「教我 X 然后考考我」)自然处理
  - 每个工具调用完成后由 `AgentCallRecorder` 记录,天然产生可视化数据
  - 加新工具 = 在 `LearningTools` 加一个 `@Tool` 方法,不动 Orchestrator

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

### 场景 1:用户「我想学 Python 装饰器」(含记忆)

```
1. POST /api/chat {message, sessionId?}
2. ChatController:
     a. getOrCreateSession(userId, sessionId) → 取/建会话
     b. 存 USER 消息到 chat_message(成为下次记忆)
     c. 读该会话最近 10 条历史 → 注入 AgentContext.dbHistory
     d. 设置 UserContext(userId) + CallContext(requestId)
3. Orchestrator.dispatch():
     a. buildSystemPrompt(ctx) 注入 DB 历史
     b. 把 5 个 @Tool 注册给 LLM (.tools(tools))
     c. LLM 自主选择:planLearningPath + generateQuiz + recommendResources
     d. LoggingToolCallingManager 拦截每次工具调用,记录入参/返回值/耗时
     e. generateQuiz 落库生成 quizId(存入 QuizResultHolder)
4. 存 ASSISTANT 回复到 chat_message
5. 返回 {reply, tools[], detail, quiz?, sessionId}
6. 前端:消息气泡 + QuizCard + GET /api/agent-calls 画调用链
```

### 场景 2:用户答题提交(Quiz 闭环)

```
1. 前端 QuizCard:用户作答 → POST /api/quiz/{quizId}/grade {answers[]}
2. QuizService.grade():
     a. 逐题比对(CHOICE 精确匹配 / FILL trim 忽略大小写)
     b. 写 attempt 记录(每题一条,is_correct/score)
     c. 错题写 wrong_book(UNIQUE 冲突则更新 master_level;做对升级到 2)
     d. 更新 knowledge_progress(attempt_count++,correct_count++,重算 mastery)
3. 返回 {graded[], summary: {total, correct, accuracy}}
4. 前端展示对错标记 + 正确答案 + 解析
5. 之后用户说"复盘一下" → reviewProgress 工具从 DB 读真实统计生成反馈
```

### 场景 3:用户上传 PDF,基于资料答疑(RAG)

```
1. POST /api/rag/upload (multipart)
2. DocumentIngestor: PDF → 切片(600 字/80 重叠)→ 嵌入 → 写入内存向量库
3. POST /api/chat {message: "..."}
4. LLM 选中 answerQuestion 工具:
     a. Retriever.retrieve(question, topK=5) → 检索命中片段
     b. 命中片段拼进 system prompt(带来源序号 [n])
     c. LLM 基于片段回答,TutorResult.ragUsed=true
5. 返回 {answer, ragUsed}
```

## 七、扩展点

- **新工具**:在 `LearningTools` 加一个 `@Tool` 方法,Orchestrator 不用改(LLM 自动发现)
- **新学科**:在 `subject` 表插一条 + 知识树 JSON
- **新 LLM 厂商**:Spring AI 已支持多家;`.env` 切 base-url 即可
- **离线模式**:`LLM_FALLBACK_MOCK=true` 时,所有工具返回 fallback 但仍走完整调用链(便于演示)

## 八、安全

- API Key 仅在 `.env`,不入库
- 密码 bcrypt(rounds=10,生产建议 12)
- JWT HS256,TTL 72h,生产建议接 Redis 黑名单
- 上传文件大小限制(RAG_MAX_UPLOAD_MB)
- CORS 仅放行 `FRONTEND_ORIGIN`
- 默认拒绝请求体过大的接口(防 DoS)

# Learning Buddy · Backend

> Spring Boot 3.3 + Java 21 + Spring AI + zvec

## 模块概览

```
com.learningbuddy
├── LearningBuddyApplication    # 入口
├── api/                        # 路由层(Controllers)
│   ├── HealthController        # GET  /api/health, /api/agents
│   ├── AuthController          # 注册/登录/me
│   ├── ChatController          # POST /api/chat, GET /api/agent-calls
│   ├── RagController           # POST /api/rag/upload
│   └── GlobalExceptionHandler
├── agents/                     # 多智能体
│   ├── BaseAgent               # 统一接口
│   ├── AgentContext            # 请求上下文(贯穿所有 Agent)
│   ├── AgentResult             # 统一返回
│   ├── Orchestrator            # 协调 Agent(意图 + 调度 + 汇总)
│   ├── PlannerAgent            # 路径规划
│   ├── QuizAgent               # 出题
│   ├── TutorAgent              # 答疑(支持 RAG)
│   ├── RecommenderAgent        # 资源推荐
│   └── ReviewerAgent           # 复盘
├── graph/
│   └── AgentCallRecorder       # 调用链日志(可视化用)
├── core/
│   └── LlmClient               # Spring AI 客户端封装
├── rag/
│   ├── DocumentIngestor        # 文档切片 + 嵌入
│   └── Retriever               # 检索(zvec 优先,降级内存)
├── security/
│   ├── SecurityConfig          # Spring Security
│   ├── JwtService              # JWT 签发/解析
│   └── JwtAuthFilter           # 鉴权过滤器
├── config/
│   ├── AppConfig               # 启用 @ConfigurationProperties
│   └── PropertiesConfig        # 自定义配置
└── services/
    └── KnowledgeSeedRunner     # 种子数据(D2 切到 JPA)
```

## 跑起来

```bash
# 1. 准备 JDK 21 + Maven
brew install openjdk@21 maven
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"

# 2. 配 .env(项目根)
cp ../.env.example ../.env
# 编辑 .env 填 LLM_API_KEY

# 3. 启动
mvn spring-boot:run
# 验证:curl http://localhost:8080/api/health
```

## 关键依赖

- `spring-boot-starter-web` — REST
- `spring-boot-starter-security` + `jjwt` — 鉴权
- `spring-boot-starter-data-jpa` + `h2` — 关系库(默认)
- `spring-ai-starter-model-openai` — LLM 客户端(对接 MiniMax M3)
- `tika-parsers-standard-package` — 文档解析(RAG)
- `com.alibaba.zvec:zvec-core` — 向量库
- `lombok` — 减少样板

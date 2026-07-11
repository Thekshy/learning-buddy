/**
 * 前后端共享的数据类型定义。
 * 字段与后端 DTO 保持一致:
 *   @see backend/com/learningbuddy/api/AuthController.java   (AuthResp)
 *   @see backend/com/learningbuddy/api/ChatController.java   (chat / agent-calls 返回体)
 */

/** 单次 Agent 调用记录(对应后端 AgentCallRecorder 持久化的节点) */
export type AgentCall = {
  id: number;
  requestId: string;
  parentCallId: number | null;
  agentName: string;
  action: string;
  inputSummary?: string;
  outputSummary?: string;
  status: "RUNNING" | "SUCCESS" | "FAILED" | string;
  durationMs?: number;
};

/** 聊天消息流中的单条消息(用户 / 助手) */
export type ChatMessage =
  | { role: "user"; text: string }
  | {
      role: "assistant";
      text: string;
      intent?: string;
      calls?: AgentCall[];
      detail?: unknown;
      /** 若本条消息触发了 generateQuiz,这里携带解析出的 quiz 数据供 QuizCard 渲染 */
      quiz?: { quizId: number | null; questions: QuizQuestion[] };
    };

/* ----------------------------- 鉴权 ----------------------------- */

export type AuthResponse = {
  token: string;
  userId: number;
  username: string;
  displayName?: string;
};

/** 后端在 !ok 时统一返回的报错体 */
export type ApiErrorBody = { error: string };

/* ----------------------------- 聊天 ----------------------------- */

export type ChatResponse = {
  requestId: string;
  sessionId: number;
  tools: string[];
  reply: string;
  detail: unknown;
  /** 本次请求若触发了出题,携带 quiz 数据供 QuizCard 渲染 */
  quiz?: { quizId: number | null; questions: QuizQuestion[] };
};

export type AgentCallsResponse = {
  calls: AgentCall[];
};

/* ----------------------------- 会话 ----------------------------- */

export type ChatSession = {
  id: number;
  title: string;
  agentKind: string;
  createdAt: string;
  updatedAt: string;
};

export type SessionListResponse = { sessions: ChatSession[] };

export type SessionMessage = {
  id: number;
  role: string;
  content: string;
  agentKind: string;
  createdAt: string;
};

export type SessionMessagesResponse = { messages: SessionMessage[] };

/* ----------------------------- Quiz 闭环 ----------------------------- */

/** 后端 LearningTools.Question 的前端镜像 */
export type QuizQuestion = {
  questionId?: number;   // 落库后有;未落库时无
  type: string;          // CHOICE / FILL
  stem: string;
  options: string[] | null;
  answer: string;
  analysis: string;
};

export type QuizResult = {
  questions: QuizQuestion[];
  quizType: string;
};

/** /api/quiz/{id}/grade 判分接口 */
export type GradeRequest = {
  answers: { questionId: number; userAnswer: string }[];
};

export type GradeResponse = {
  quizId: number;
  graded: {
    questionId: number;
    correct: boolean;
    userAnswer: string;
    correctAnswer: string;
    analysis: string;
  }[];
  summary: { total: number; correct: number; accuracy: number };
};

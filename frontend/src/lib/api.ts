import { getToken } from "./auth";
import type {
  AgentCallsResponse,
  AuthResponse,
  ChatResponse,
  GradeRequest,
  GradeResponse,
  SessionListResponse,
  SessionMessagesResponse,
} from "./types";

/**
 * 统一请求层。
 *
 * 之前两个页面各自裸 fetch,存在三个重复痛点:
 *   1. 每次 GET/POST 都要手写 Authorization 头
 *   2. 错误处理只有 `e: any` → `.message`,丢失了 HTTP 状态码
 *   3. 返回值无类型,`data.reply` / `data.requestId` 全是 any
 *
 * 现在统一在此注入鉴权头、抛出带 status 的 ApiError、并对返回值做强类型约束。
 * 路径仍走 next.config.js 的 rewrites(`/api/*` → 后端 :8080),契约不变。
 */

/** 带 HTTP 状态码的错误,替代裸 `catch (e: any)`。 */
export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

type RequestOptions = {
  method?: "GET" | "POST";
  /** 需要 Authorization 的接口传 true(默认按需自动注入)。 */
  auth?: boolean;
  /** POST 请求体,会自动 JSON.stringify。 */
  body?: unknown;
  /** GET 查询参数。 */
  query?: Record<string, string | undefined>;
};

/** 底层请求:拼前缀、注入鉴权、解析错误。 */
async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = "GET", auth = true, body, query } = opts;

  let url = path;
  if (query) {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(query)) {
      if (v != null) qs.set(k, v);
    }
    const s = qs.toString();
    if (s) url += `?${s}`;
  }

  const headers: Record<string, string> = {};
  if (method === "POST") headers["Content-Type"] = "application/json";
  if (auth) {
    const token = getToken();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(url, {
    method,
    headers,
    body: body != null ? JSON.stringify(body) : undefined,
  });

  let data: unknown;
  try {
    data = await res.json();
  } catch {
    data = null;
  }

  if (!res.ok) {
    const msg =
      (data as { error?: string } | null)?.error ?? `${res.status} ${res.statusText}`;
    throw new ApiError(msg, res.status);
  }

  return data as T;
}

/** 鉴权相关接口。 */
export const authApi = {
  register: (username: string, password: string) =>
    request<AuthResponse>("/api/auth/register", {
      method: "POST",
      auth: false,
      body: { username, password },
    }),

  login: (username: string, password: string) =>
    request<AuthResponse>("/api/auth/login", {
      method: "POST",
      auth: false,
      body: { username, password },
    }),
};

/** 聊天相关接口。 */
export const chatApi = {
  /** 发消息;history 和 sessionId 用于多轮记忆(双保险)。 */
  send: (
    message: string,
    useRag: boolean,
    history?: string[],
    sessionId?: number,
  ) =>
    request<ChatResponse>("/api/chat", {
      method: "POST",
      body: { message, useRag, history, sessionId },
    }),

  agentCalls: (requestId: string) =>
    request<AgentCallsResponse>("/api/agent-calls", {
      query: { request_id: requestId },
    }),
};

/** 会话管理接口。 */
export const sessionApi = {
  create: (title?: string) =>
    request<{ id: number; title: string }>("/api/sessions", {
      method: "POST",
      body: title ? { title } : {},
    }),

  list: () => request<SessionListResponse>("/api/sessions"),

  messages: (sessionId: number) =>
    request<SessionMessagesResponse>(`/api/sessions/${sessionId}/messages`),
};

/** Quiz 闭环接口。 */
export const quizApi = {
  grade: (quizId: number, body: GradeRequest) =>
    request<GradeResponse>(`/api/quiz/${quizId}/grade`, {
      method: "POST",
      body,
    }),
};

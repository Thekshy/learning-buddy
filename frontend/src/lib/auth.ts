import type { AuthResponse } from "./types";

/**
 * 鉴权状态在浏览器 localStorage 中的存取收口。
 * 之前各页面散落着 `localStorage.getItem("lb_token")` / `localStorage.setItem(...)`,
 * 统一到这里避免 key 名漂移与清理不彻底。
 *
 * Token 机制本身不变:仍由后端签发 JWT,前端作为 Bearer 携带。
 */

export const TOKEN_KEY = "lb_token";
export const USER_KEY = "lb_user";

export type StoredUser = { id: number; username: string };

/** 读取 token,不存在返回 null。 */
export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

/** 读取已登录用户,不存在或格式损坏返回 null。 */
export function getUser(): StoredUser | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredUser;
  } catch {
    return null;
  }
}

/** 登录/注册成功后持久化鉴权信息。 */
export function saveAuth(resp: AuthResponse): void {
  localStorage.setItem(TOKEN_KEY, resp.token);
  localStorage.setItem(
    USER_KEY,
    JSON.stringify({ id: resp.userId, username: resp.username } satisfies StoredUser),
  );
}

/** 退出登录:仅清除 lb_* 相关键,不动 localStorage 里的其它内容。 */
export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

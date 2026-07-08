"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

export default function HomePage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [mode, setMode] = useState<"login" | "register">("register");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function submit() {
    setBusy(true); setErr(null);
    try {
      const url = mode === "register" ? "/api/auth/register" : "/api/auth/login";
      const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "failed");
      localStorage.setItem("lb_token", data.token);
      localStorage.setItem("lb_user", JSON.stringify({ id: data.userId, username: data.username }));
      router.push("/chat");
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-gradient-to-br from-brand-50 via-white to-emerald-50">
      <div className="w-full max-w-md p-8 rounded-2xl shadow-xl bg-white">
        <h1 className="text-3xl font-bold text-brand-700 mb-2">Learning Buddy</h1>
        <p className="text-slate-500 mb-6 text-sm">
          多智能体学习伙伴 · Orchestrator + 5 个专职 Agent 协作
        </p>
        <div className="flex gap-2 mb-4 text-sm">
          <button
            onClick={() => setMode("register")}
            className={`flex-1 py-2 rounded-lg ${mode === "register" ? "bg-brand-600 text-white" : "bg-slate-100 text-slate-600"}`}>
            注册
          </button>
          <button
            onClick={() => setMode("login")}
            className={`flex-1 py-2 rounded-lg ${mode === "login" ? "bg-brand-600 text-white" : "bg-slate-100 text-slate-600"}`}>
            登录
          </button>
        </div>
        <label className="block text-sm text-slate-600 mb-1">用户名</label>
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="w-full mb-3 px-3 py-2 rounded-lg border border-slate-200 focus:border-brand-500 focus:ring-2 focus:ring-brand-100 outline-none"
          placeholder="至少 3 个字符" />
        <label className="block text-sm text-slate-600 mb-1">密码</label>
        <input
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          type="password"
          className="w-full mb-4 px-3 py-2 rounded-lg border border-slate-200 focus:border-brand-500 focus:ring-2 focus:ring-brand-100 outline-none"
          placeholder="至少 6 个字符" />
        {err && <p className="text-rose-500 text-sm mb-3">{err}</p>}
        <button
          onClick={submit}
          disabled={busy}
          className="w-full py-3 rounded-lg bg-brand-600 text-white font-semibold hover:bg-brand-700 disabled:opacity-50">
          {busy ? "处理中…" : mode === "register" ? "创建账号" : "登录"}
        </button>
        <p className="text-xs text-slate-400 mt-4 text-center">
          后端:{process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080"}
        </p>
      </div>
    </main>
  );
}

"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { User, Lock, Sparkles, Loader2 } from "lucide-react";
import Aurora from "@/components/ui/Aurora";
import GradientText from "@/components/ui/GradientText";

export default function HomePage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [mode, setMode] = useState<"login" | "register">("register");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function submit() {
    setBusy(true);
    setErr(null);
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
    <main className="min-h-screen relative flex items-center justify-center overflow-hidden bg-slate-950">
      {/* Aurora 极光背景 — react-bits */}
      <div className="absolute inset-0 z-0">
        <Aurora
          colorStops={["#4f46e5", "#10b981", "#a855f7"]}
          amplitude={1.2}
          blend={0.6}
          speed={0.6}
        />
      </div>

      {/* 内容卡片 */}
      <div className="relative z-10 w-full max-w-md p-8 rounded-2xl shadow-2xl bg-white/95 backdrop-blur-sm">
        <div className="flex items-center gap-2 mb-2">
          <Sparkles className="w-5 h-5 text-brand-600" />
          <GradientText
            colors={["#4f46e5", "#a855f7", "#10b981", "#4f46e5"]}
            animationSpeed={6}
            className="!mx-0 text-3xl font-bold"
          >
            Learning Buddy
          </GradientText>
        </div>
        <p className="text-slate-500 mb-6 text-sm">
          多智能体学习伙伴 · Orchestrator + 5 个专职 Agent 协作
        </p>

        <div className="flex gap-2 mb-5 text-sm">
          <button
            onClick={() => setMode("register")}
            className={`flex-1 py-2 rounded-lg transition ${
              mode === "register"
                ? "bg-brand-600 text-white shadow-sm"
                : "bg-slate-100 text-slate-600 hover:bg-slate-200"
            }`}
          >
            注册
          </button>
          <button
            onClick={() => setMode("login")}
            className={`flex-1 py-2 rounded-lg transition ${
              mode === "login"
                ? "bg-brand-600 text-white shadow-sm"
                : "bg-slate-100 text-slate-600 hover:bg-slate-200"
            }`}
          >
            登录
          </button>
        </div>

        <label className="block text-sm text-slate-600 mb-1">用户名</label>
        <div className="relative mb-3">
          <User className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="w-full pl-9 pr-3 py-2 rounded-lg border border-slate-200 focus:border-brand-500 focus:ring-2 focus:ring-brand-100 outline-none transition"
            placeholder="至少 3 个字符"
          />
        </div>

        <label className="block text-sm text-slate-600 mb-1">密码</label>
        <div className="relative mb-4">
          <Lock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            type="password"
            className="w-full pl-9 pr-3 py-2 rounded-lg border border-slate-200 focus:border-brand-500 focus:ring-2 focus:ring-brand-100 outline-none transition"
            placeholder="至少 6 个字符"
          />
        </div>

        {err && <p className="text-rose-500 text-sm mb-3">{err}</p>}

        <button
          onClick={submit}
          disabled={busy}
          className="w-full flex items-center justify-center gap-2 py-3 rounded-lg bg-brand-600 text-white font-semibold hover:bg-brand-700 disabled:opacity-50 transition shadow-sm"
        >
          {busy ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              处理中…
            </>
          ) : mode === "register" ? (
            "创建账号"
          ) : (
            "登录"
          )}
        </button>

        <p className="text-xs text-slate-400 mt-4 text-center">
          后端:{process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080"}
        </p>
      </div>
    </main>
  );
}

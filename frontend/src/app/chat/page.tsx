"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";

/**
 * 聊天主界面 — 体现"多智能体协作"
 *  - 左侧:对话流
 *  - 右侧:Agent 调用链时间线(评分核心可视化)
 *  - 输入:支持开关 RAG
 */

type AgentCall = {
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

type ChatMessage =
  | { role: "user"; text: string }
  | { role: "assistant"; text: string; intent?: string; calls?: AgentCall[]; detail?: any };

export default function ChatPage() {
  const router = useRouter();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [useRag, setUseRag] = useState(false);
  const [busy, setBusy] = useState(false);
  const [calls, setCalls] = useState<AgentCall[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!localStorage.getItem("lb_token")) router.push("/");
  }, [router]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  async function send() {
    const text = input.trim();
    if (!text || busy) return;
    setInput("");
    setBusy(true);
    setMessages((m) => [...m, { role: "user", text }]);
    try {
      const token = localStorage.getItem("lb_token") || "";
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ message: text, useRag }),
      });
      const data = await res.json();
      setMessages((m) => [...m, { role: "assistant", text: data.reply, intent: data.intent, detail: data.detail }]);
      // 拉取调用链
      const callsRes = await fetch(`/api/agent-calls?request_id=${data.requestId}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const callsData = await callsRes.json();
      setCalls(callsData.calls || []);
    } catch (e: any) {
      setMessages((m) => [...m, { role: "assistant", text: "出错了:" + e.message }]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="h-screen flex bg-slate-50">
      {/* 左侧:对话流 */}
      <section className="flex-1 flex flex-col">
        <header className="h-14 flex items-center justify-between px-6 bg-white border-b border-slate-200">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-brand-600 text-white grid place-items-center font-bold">LB</div>
            <h1 className="font-semibold">Learning Buddy</h1>
          </div>
          <div className="flex items-center gap-4 text-sm">
            <label className="flex items-center gap-2 text-slate-600">
              <input type="checkbox" checked={useRag} onChange={(e) => setUseRag(e.target.checked)} />
              使用 RAG 检索
            </label>
            <button
              onClick={() => { localStorage.clear(); router.push("/"); }}
              className="text-slate-400 hover:text-rose-500">退出</button>
          </div>
        </header>
        <div ref={scrollRef} className="flex-1 overflow-y-auto p-6 space-y-4">
          {messages.length === 0 && (
            <div className="text-center text-slate-400 mt-20">
              <p className="text-lg mb-2">👋 你好,我是你的智能学习伙伴</p>
              <p className="text-sm">试着说:"我想学 Python 装饰器,我是初学者"</p>
            </div>
          )}
          {messages.map((m, i) => (
            <MessageBubble key={i} msg={m} />
          ))}
          {busy && <div className="text-slate-400 text-sm">🤔 多 Agent 协作中…</div>}
        </div>
        <div className="p-4 bg-white border-t border-slate-200 flex gap-2">
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && send()}
            placeholder="输入你的需求…"
            className="flex-1 px-4 py-3 rounded-lg border border-slate-200 focus:border-brand-500 outline-none"
            disabled={busy} />
          <button
            onClick={send}
            disabled={busy}
            className="px-6 py-3 rounded-lg bg-brand-600 text-white font-semibold hover:bg-brand-700 disabled:opacity-50">
            发送
          </button>
        </div>
      </section>

      {/* 右侧:Agent 调用链 */}
      <aside className="w-96 border-l border-slate-200 bg-white overflow-y-auto">
        <div className="p-4 border-b border-slate-200">
          <h2 className="font-semibold text-slate-700">Agent 调用链</h2>
          <p className="text-xs text-slate-400 mt-1">实时展示本次请求触发的多 Agent 协作</p>
        </div>
        <div className="p-4 space-y-3">
          {calls.length === 0 && (
            <p className="text-sm text-slate-400">还没有调用。发条消息试试。</p>
          )}
          {calls.map((c) => (
            <div key={c.id} className="rounded-lg border border-slate-200 p-3">
              <div className="flex items-center justify-between mb-1">
                <span className="font-semibold text-brand-700">{c.agentName}</span>
                <span className={`text-xs px-2 py-0.5 rounded-full ${
                  c.status === "SUCCESS" ? "bg-emerald-100 text-emerald-700" :
                  c.status === "FAILED" ? "bg-rose-100 text-rose-700" :
                  "bg-amber-100 text-amber-700 animate-pulse-soft"
                }`}>{c.status}</span>
              </div>
              <div className="text-xs text-slate-500">动作:{c.action}</div>
              {c.durationMs != null && <div className="text-xs text-slate-500">耗时:{c.durationMs} ms</div>}
              {c.outputSummary && (
                <div className="mt-2 text-xs text-slate-700 line-clamp-3 bg-slate-50 p-2 rounded">
                  {c.outputSummary}
                </div>
              )}
            </div>
          ))}
        </div>
      </aside>
    </main>
  );
}

function MessageBubble({ msg }: { msg: ChatMessage }) {
  if (msg.role === "user") {
    return (
      <div className="flex justify-end">
        <div className="max-w-lg px-4 py-3 rounded-2xl bg-brand-600 text-white">{msg.text}</div>
      </div>
    );
  }
  return (
    <div className="flex flex-col items-start">
      {msg.intent && (
        <div className="text-xs text-slate-400 mb-1">意图:<code className="bg-slate-100 px-1.5 py-0.5 rounded">{msg.intent}</code></div>
      )}
      <div className="max-w-2xl px-4 py-3 rounded-2xl bg-white border border-slate-200 whitespace-pre-wrap">
        {msg.text}
      </div>
      {msg.detail?.detail?.agentResults && (
        <div className="mt-2 ml-2 text-xs text-slate-500">
          调动了 {msg.detail.detail.agentResults.length} 个 Agent
        </div>
      )}
    </div>
  );
}

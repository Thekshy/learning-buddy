"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Send, LogOut, BookOpen, Sparkles, Loader2, Brain } from "lucide-react";
import BlurText from "@/components/ui/BlurText";
import MessageBubble from "@/components/chat/MessageBubble";
import AgentChainStepper from "@/components/chat/AgentChainStepper";
import { chatApi, ApiError } from "@/lib/api";
import { getToken, clearAuth } from "@/lib/auth";
import type { AgentCall, ChatMessage } from "@/lib/types";

/**
 * 聊天主界面 — react-bits 视觉特效
 *  - 左侧:对话流 + BlurText 欢迎语
 *  - 右侧:Stepper Agent 调用链(评分核心)
 */

export default function ChatPage() {
  const router = useRouter();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [useRag, setUseRag] = useState(false);
  const [busy, setBusy] = useState(false);
  const [calls, setCalls] = useState<AgentCall[]>([]);
  const [sessionId, setSessionId] = useState<number | undefined>(undefined);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!getToken()) router.push("/");
  }, [router]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  async function send() {
    const text = input.trim();
    if (!text || busy) return;
    setInput("");
    setBusy(true);
    // 双保险之"前端传历史":把已有消息映射成字符串数组发给后端
    const history = messages.map((m) =>
      `${m.role === "user" ? "USER" : "ASSISTANT"}: ${m.text}`,
    );
    setMessages((m) => [...m, { role: "user", text }]);
    try {
      const data = await chatApi.send(text, useRag, history, sessionId);
      // 记住后端分配的 sessionId,后续请求带上(让后端把消息归到同一会话)
      if (data.sessionId) setSessionId(data.sessionId);
      setMessages((m) => [
        ...m,
        {
          role: "assistant",
          text: data.reply,
          detail: data.detail,
          quiz: data.quiz,
        },
      ]);
      // 拉取调用链
      const callsData = await chatApi.agentCalls(data.requestId);
      setCalls(callsData.calls || []);
    } catch (e: unknown) {
      const msg = e instanceof ApiError ? e.message : String(e);
      setMessages((m) => [...m, { role: "assistant", text: "出错了:" + msg }]);
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
            <div className="w-8 h-8 rounded-lg bg-brand-600 text-white grid place-items-center">
              <BookOpen className="w-4 h-4" />
            </div>
            <h1 className="font-semibold">Learning Buddy</h1>
          </div>
          <div className="flex items-center gap-4 text-sm">
            <label className="flex items-center gap-2 text-slate-600 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={useRag}
                onChange={(e) => setUseRag(e.target.checked)}
                className="accent-brand-600"
              />
              使用 RAG 检索
            </label>
            <button
              onClick={() => {
                clearAuth();
                router.push("/");
              }}
              className="flex items-center gap-1 text-slate-400 hover:text-rose-500 transition"
            >
              <LogOut className="w-4 h-4" />
              退出
            </button>
          </div>
        </header>

        <div ref={scrollRef} className="flex-1 overflow-y-auto p-6 space-y-4">
          {messages.length === 0 && (
            <div className="text-center text-slate-500 mt-20">
              <div className="flex justify-center mb-4">
                <Sparkles className="w-10 h-10 text-brand-500" />
              </div>
              <BlurText
                text="你好,我是你的智能学习伙伴"
                className="text-2xl font-semibold justify-center mb-3"
                delay={120}
                animateBy="words"
              />
              <p className="text-sm text-slate-400">试着说:&quot;我想学 Python 装饰器,我是初学者&quot;</p>
            </div>
          )}
          {messages.map((m, i) => (
            <MessageBubble key={i} msg={m} />
          ))}
          {busy && (
            <div className="flex items-center gap-2 text-slate-400 text-sm">
              <Loader2 className="w-4 h-4 animate-spin" />
              多 Agent 协作中…
            </div>
          )}
        </div>

        <div className="p-4 bg-white border-t border-slate-200 flex gap-2">
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && send()}
            placeholder="输入你的需求…"
            className="flex-1 px-4 py-3 rounded-lg border border-slate-200 focus:border-brand-500 outline-none transition"
            disabled={busy}
          />
          <button
            onClick={send}
            disabled={busy}
            className="flex items-center gap-2 px-6 py-3 rounded-lg bg-brand-600 text-white font-semibold hover:bg-brand-700 disabled:opacity-50 transition"
          >
            <Send className="w-4 h-4" />
            发送
          </button>
        </div>
      </section>

      {/* 右侧:Agent 调用链 — react-bits Stepper(评分核心) */}
      <aside className="w-[420px] border-l border-slate-200 bg-white overflow-y-auto p-4">
        <div className="mb-3 flex items-center gap-2">
          <Brain className="w-4 h-4 text-brand-600" />
          <h2 className="font-semibold text-slate-700 text-sm">Agent 调用链</h2>
        </div>
        {calls.length === 0 ? (
          <p className="text-xs text-slate-400 mt-6 text-center">还没有调用。发条消息试试。</p>
        ) : (
          <AgentChainStepper calls={calls} />
        )}
      </aside>
    </main>
  );
}

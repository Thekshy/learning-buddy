"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Send, LogOut, BookOpen, Sparkles, CheckCircle2, XCircle, Loader2, Brain } from "lucide-react";
import BlurText from "@/components/ui/BlurText";
import Stepper, { Step } from "@/components/ui/Stepper";

/**
 * 聊天主界面 — react-bits 视觉特效
 *  - 左侧:对话流 + BlurText 欢迎语
 *  - 右侧:Stepper Agent 调用链(评分核心)
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
                localStorage.clear();
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
          <p className="text-xs text-slate-400 mt-6 text-center">
            还没有调用。发条消息试试。
          </p>
        ) : (
          <AgentChainStepper calls={calls} />
        )}
      </aside>
    </main>
  );
}

function AgentChainStepper({ calls }: { calls: AgentCall[] }) {
  return (
    <Stepper
      initialStep={1}
      backButtonText="上一步"
      nextButtonText="下一步"
      stepCircleContainerClassName="!max-w-none !w-full"
    >
      {calls.map((c) => (
        <Step key={c.id}>
          <AgentCallCard call={c} />
        </Step>
      ))}
    </Stepper>
  );
}

function AgentCallCard({ call }: { call: AgentCall }) {
  const statusMap: Record<string, { icon: React.ReactNode; cls: string; label: string }> = {
    SUCCESS: {
      icon: <CheckCircle2 className="w-4 h-4" />,
      cls: "bg-emerald-100 text-emerald-700",
      label: "成功",
    },
    FAILED: {
      icon: <XCircle className="w-4 h-4" />,
      cls: "bg-rose-100 text-rose-700",
      label: "失败",
    },
    RUNNING: {
      icon: <Loader2 className="w-4 h-4 animate-spin" />,
      cls: "bg-amber-100 text-amber-700",
      label: "运行中",
    },
  };
  const s = statusMap[call.status] ?? {
    icon: <Loader2 className="w-4 h-4 animate-spin" />,
    cls: "bg-amber-100 text-amber-700",
    label: call.status,
  };

  return (
    <div className="py-2">
      <div className="flex items-center justify-between mb-2">
        <span className="font-semibold text-brand-700 text-sm">{call.agentName}</span>
        <span
          className={`flex items-center gap-1 text-xs px-2 py-0.5 rounded-full ${s.cls}`}
        >
          {s.icon}
          {s.label}
        </span>
      </div>
      <div className="text-xs text-slate-500 mb-1">
        <span className="text-slate-400">动作:</span> {call.action}
      </div>
      {call.durationMs != null && (
        <div className="text-xs text-slate-500 mb-2">
          <span className="text-slate-400">耗时:</span> {call.durationMs} ms
        </div>
      )}
      {call.outputSummary && (
        <div className="mt-2 text-xs text-slate-700 bg-slate-50 p-3 rounded-lg border border-slate-100 max-h-40 overflow-y-auto">
          {call.outputSummary}
        </div>
      )}
    </div>
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
        <div className="text-xs text-slate-400 mb-1">
          意图:
          <code className="bg-slate-100 px-1.5 py-0.5 rounded ml-1">{msg.intent}</code>
        </div>
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

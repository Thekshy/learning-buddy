import type { ChatMessage } from "@/lib/types";
import QuizCard from "./QuizCard";

/**
 * 单条聊天消息气泡。
 * 用户消息右对齐 brand 底色;助手消息左对齐白底,带意图标签与 Agent 计数。
 * 若本条触发了出题,在气泡下渲染 QuizCard 答题卡片。
 */
export default function MessageBubble({ msg }: { msg: ChatMessage }) {
  if (msg.role === "user") {
    return (
      <div className="flex justify-end">
        <div className="max-w-lg px-4 py-3 rounded-2xl bg-brand-600 text-white">{msg.text}</div>
      </div>
    );
  }

  // 后端 detail 结构较灵活,这里用类型守卫取出 agentResults 计数用于展示。
  const agentCount = readAgentCount(msg.detail);

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
      {agentCount != null && (
        <div className="mt-2 ml-2 text-xs text-slate-500">调动了 {agentCount} 个 Agent</div>
      )}
      {msg.quiz && msg.quiz.questions.length > 0 && (
        <div className="max-w-2xl w-full">
          <QuizCard quizId={msg.quiz.quizId} questions={msg.quiz.questions} />
        </div>
      )}
    </div>
  );
}

/** 从后端 detail.detail.agentResults(数组)中安全取出长度,无则返回 null。 */
function readAgentCount(detail: unknown): number | null {
  if (typeof detail !== "object" || detail === null) return null;
  const inner = (detail as { detail?: unknown }).detail;
  if (typeof inner !== "object" || inner === null) return null;
  const results = (inner as { agentResults?: unknown }).agentResults;
  return Array.isArray(results) ? results.length : null;
}

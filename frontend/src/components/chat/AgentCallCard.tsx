import { CheckCircle2, XCircle, Loader2 } from "lucide-react";
import type { AgentCall } from "@/lib/types";

/**
 * Agent 调用链中单个节点的卡片:Agent 名 + 状态徽章 + 动作 + 耗时 + 输出摘要。
 */

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

export default function AgentCallCard({ call }: { call: AgentCall }) {
  const s =
    statusMap[call.status] ?? {
      icon: <Loader2 className="w-4 h-4 animate-spin" />,
      cls: "bg-amber-100 text-amber-700",
      label: call.status,
    };

  return (
    <div className="py-2">
      <div className="flex items-center justify-between mb-2">
        <span className="font-semibold text-brand-700 text-sm">{call.agentName}</span>
        <span className={`flex items-center gap-1 text-xs px-2 py-0.5 rounded-full ${s.cls}`}>
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

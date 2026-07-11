import { useState } from "react";
import { CheckCircle2, XCircle, Loader2, ChevronDown, ChevronRight } from "lucide-react";
import type { AgentCall } from "@/lib/types";

/**
 * Agent 调用链中单个节点的卡片。
 *
 * 展示:Agent 名 + 状态徽章 + 动作 + 耗时 + 入参 + 返回值(可折叠)。
 * 根节点(Orchestrator)样式更突出。
 */

const statusMap: Record<string, { icon: React.ReactNode; cls: string; label: string }> = {
  SUCCESS: {
    icon: <CheckCircle2 className="w-3.5 h-3.5" />,
    cls: "bg-emerald-100 text-emerald-700",
    label: "成功",
  },
  FAILED: {
    icon: <XCircle className="w-3.5 h-3.5" />,
    cls: "bg-rose-100 text-rose-700",
    label: "失败",
  },
  RUNNING: {
    icon: <Loader2 className="w-3.5 h-3.5 animate-spin" />,
    cls: "bg-amber-100 text-amber-700",
    label: "运行中",
  },
};

// 工具名 → 中文友好名
const agentLabels: Record<string, string> = {
  Orchestrator: "协调 Agent",
  planLearningPath: "学习路径规划",
  generateQuiz: "出题",
  answerQuestion: "答疑",
  recommendResources: "资源推荐",
  reviewProgress: "复盘",
};

export default function AgentCallCard({ call, isRoot = false }: { call: AgentCall; isRoot?: boolean }) {
  const [expanded, setExpanded] = useState(false);
  const s =
    statusMap[call.status] ?? {
      icon: <Loader2 className="w-3.5 h-3.5 animate-spin" />,
      cls: "bg-amber-100 text-amber-700",
      label: call.status,
    };

  const friendlyName = agentLabels[call.agentName] ?? call.agentName;
  const hasDetail = (call.inputSummary && call.inputSummary.length > 0) ||
                    (call.outputSummary && call.outputSummary.length > 0);

  return (
    <div className={`rounded-lg ${isRoot ? "bg-brand-50 border border-brand-200" : "bg-white border border-slate-100"}`}>
      <button
        onClick={() => hasDetail ? setExpanded(!expanded) : undefined}
        className={`w-full flex items-center justify-between px-3 py-2 ${hasDetail ? "cursor-pointer hover:bg-slate-50" : "cursor-default"} rounded-t-lg`}
      >
        <div className="flex items-center gap-1.5 min-w-0">
          {hasDetail ? (
            expanded
              ? <ChevronDown className="w-3 h-3 text-slate-400 flex-shrink-0" />
              : <ChevronRight className="w-3 h-3 text-slate-400 flex-shrink-0" />
          ) : (
            <span className="w-3 h-3 flex-shrink-0" />
          )}
          <span className={`font-medium text-sm truncate ${isRoot ? "text-brand-700" : "text-slate-700"}`}>
            {friendlyName}
          </span>
          {!isRoot && (
            <code className="text-[10px] text-slate-400 bg-slate-100 px-1 rounded truncate">{call.agentName}</code>
          )}
        </div>
        <span className={`flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded-full flex-shrink-0 ${s.cls}`}>
          {s.icon}
          {s.label}
        </span>
      </button>

      {/* 耗时 */}
      {call.durationMs != null && (
        <div className={`px-3 ${expanded && hasDetail ? "" : "pb-2"} text-[11px] text-slate-400`}>
          ⏱ {call.durationMs} ms
        </div>
      )}

      {/* 展开的详情:入参 + 返回值 */}
      {expanded && hasDetail && (
        <div className="px-3 pb-2 space-y-2">
          {call.inputSummary && (
            <DetailBlock label="入参" content={call.inputSummary} />
          )}
          {call.outputSummary && (
            <DetailBlock label={call.status === "FAILED" ? "错误" : "返回"} content={call.outputSummary} isError={call.status === "FAILED"} />
          )}
        </div>
      )}
    </div>
  );
}

/** 入参/返回值的小块:标签 + 代码风格内容(可滚动) */
function DetailBlock({ label, content, isError = false }: { label: string; content: string; isError?: boolean }) {
  return (
    <div>
      <div className="text-[10px] text-slate-400 mb-0.5 uppercase tracking-wide">{label}</div>
      <pre
        className={`text-[11px] p-2 rounded max-h-32 overflow-auto whitespace-pre-wrap break-all ${
          isError ? "bg-rose-50 text-rose-700" : "bg-slate-50 text-slate-600"
        }`}
      >
        {content}
      </pre>
    </div>
  );
}

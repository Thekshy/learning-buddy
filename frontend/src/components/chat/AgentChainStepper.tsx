import type { AgentCall } from "@/lib/types";
import AgentCallCard from "./AgentCallCard";

/**
 * 右侧栏的 Agent 调用链可视化(时间线版)。
 *
 * 垂直铺开所有调用:Orchestrator 根节点 → 各工具节点,
 * 一屏看完整个多 Agent 协作过程,不用翻页。
 *
 * 根节点(parentCallId == null,通常是 Orchestrator)单独渲染为起点;
 * 工具节点挂在下方,用竖线连接。
 */
export default function AgentChainStepper({ calls }: { calls: AgentCall[] }) {
  // Orchestrator 根节点:parentCallId 为 null 的(通常第一个)
  const roots = calls.filter((c) => c.parentCallId == null);
  const tools = calls.filter((c) => c.parentCallId != null);

  return (
    <div className="space-y-0">
      {roots.map((root) => (
        <div key={root.id}>
          <AgentCallCard call={root} isRoot />
          {tools.length > 0 && (
            <div className="ml-2 border-l-2 border-slate-200 pl-3 space-y-2 mt-1 mb-3">
              {tools.map((t) => (
                <AgentCallCard key={t.id} call={t} />
              ))}
            </div>
          )}
        </div>
      ))}
      {/* 没有 root 时全部平铺(兼容旧数据) */}
      {roots.length === 0 &&
        tools.map((t) => <AgentCallCard key={t.id} call={t} />)}
    </div>
  );
}

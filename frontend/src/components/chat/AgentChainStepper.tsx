import Stepper, { Step } from "@/components/ui/Stepper";
import type { AgentCall } from "@/lib/types";
import AgentCallCard from "./AgentCallCard";

/**
 * 右侧栏的 Agent 调用链可视化。
 * 用 react-bits Stepper 把每条 AgentCall 渲染成一个可翻页步骤。
 */
export default function AgentChainStepper({ calls }: { calls: AgentCall[] }) {
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

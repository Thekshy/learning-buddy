package com.learningbuddy.graph;

import com.learningbuddy.agents.AgentContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 调用链记录器
 *
 * <p>职责:每次 Agent 调用产出一条结构化日志(内存中即可,后续可换持久化)。
 * <p>提供 {@link #getByRequest(String)} 供前端可视化调用链。
 *
 * <p>评分核心:**让多 Agent 协作肉眼可见**。
 */
@Slf4j
@Component
public class AgentCallRecorder {

    private final Map<Long, CallRecord> byId = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<CallRecord>> byRequest = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    public Long start(AgentContext ctx, String agentName, String action, String input) {
        return startSimple(ctx.getRequestId(), agentName, action, input);
    }

    /** 不依赖 AgentContext 的重载(供 ToolCallingManager 包装层用) */
    public Long startSimple(String requestId, String agentName, String action, String input) {
        long id = seq.getAndIncrement();
        CallRecord r = new CallRecord(
                id, requestId, null,
                agentName, action, input,
                "RUNNING", null, null,
                System.currentTimeMillis(), null, 0
        );
        byId.put(id, r);
        byRequest.computeIfAbsent(requestId, k -> new java.util.ArrayList<>()).add(r);
        log.debug("AGENT_CALL start id={} request={} agent={}", id, requestId, agentName);
        return id;
    }

    public void finish(Long callId, String status, String outputSummary, String errorMessage) {
        CallRecord r = byId.get(callId);
        if (r == null) return;
        long finished = System.currentTimeMillis();
        r.status = status;
        r.outputSummary = outputSummary;
        r.errorMessage = errorMessage;
        r.finishedAt = finished;
        r.durationMs = (int) (finished - r.startedAt);
    }

    public java.util.List<CallRecord> getByRequest(String requestId) {
        return byRequest.getOrDefault(requestId, java.util.List.of());
    }

    /** 调用记录(纯字段,便于 JSON 序列化给前端) */
    public static class CallRecord {
        public Long id;
        public String requestId;
        public Long parentCallId;
        public String agentName;
        public String action;
        public String inputSummary;
        public String status;
        public String outputSummary;
        public String errorMessage;
        public Long startedAt;
        public Long finishedAt;
        public Integer durationMs;

        public CallRecord(Long id, String requestId, Long parentCallId, String agentName, String action,
                          String inputSummary, String status, String outputSummary, String errorMessage,
                          Long startedAt, Long finishedAt, Integer durationMs) {
            this.id = id;
            this.requestId = requestId;
            this.parentCallId = parentCallId;
            this.agentName = agentName;
            this.action = action;
            this.inputSummary = inputSummary;
            this.status = status;
            this.outputSummary = outputSummary;
            this.errorMessage = errorMessage;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.durationMs = durationMs;
        }
    }
}

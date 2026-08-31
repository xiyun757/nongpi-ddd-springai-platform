'use client';

import { Brain } from 'lucide-react';

interface AgentStepCardProps {
  /** 后端 BaseAgent 发送的 "Step N: <stepResult>" 文本 */
  step: string;
}

/**
 * Manus 智能体步骤卡片 — 展示 ReAct 循环的每一步 think/act 结果
 * 仅做文本解析展示，不解析 JSON（后端 stepResult 是拼接的字符串）
 */
export function AgentStepCard({ step }: AgentStepCardProps) {
  // 匹配 "Step 1: 调用 xxx 参数 ..." 格式
  const match = step.match(/^Step\s+(\d+):\s*(.*)/);
  const num = match?.[1] ?? '?';
  const content = match?.[2] ?? step;

  return (
    <div className="flex gap-3 p-3 rounded-lg bg-blue-50 border border-blue-100">
      <div className="shrink-0 w-7 h-7 rounded-full bg-blue-600 text-white text-xs font-medium flex items-center justify-center">
        {num}
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5 text-xs text-blue-700 font-medium mb-1">
          <Brain className="w-3 h-3" />
          <span>智能体步骤 {num}</span>
        </div>
        <div className="text-sm text-gray-700 break-words whitespace-pre-wrap max-h-40 overflow-y-auto">
          {content}
        </div>
      </div>
    </div>
  );
}

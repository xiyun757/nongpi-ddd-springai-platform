'use client';

import { useState } from 'react';
import { MessageSquare, BookOpen, Bot } from 'lucide-react';
import { AiChatPanel } from '@/components/AiChatPanel';
import type { AiMode } from '@/lib/api';

/**
 * AI 智能客服主页 — 3 模式 chip 切换
 *
 * 三模式对应能力：
 *   💬 对话 → ChatClient + ChatMemory + ToolCalling
 *   📚 知识库 → RAG 检索增强（QueryRewriter + PGVector）
 *   🤖 智能体 → Manus ReAct 多步 think/act 循环
 */
const MODES: { value: AiMode; label: string; icon: typeof MessageSquare; desc: string }[] = [
  { value: 'chat', label: '对话', icon: MessageSquare, desc: '多轮对话 + 工具调用' },
  { value: 'rag', label: '知识库', icon: BookOpen, desc: '业务规范/流程问答' },
  { value: 'manus', label: '智能体', icon: Bot, desc: '多步任务规划执行' },
];

export default function AiPage() {
  const [mode, setMode] = useState<AiMode>('chat');

  return (
    <div className="flex flex-col h-[calc(100vh-3rem)]">
      {/* 模式切换 chip */}
      <div className="flex gap-2 pb-3 border-b border-gray-200">
        {MODES.map((m) => {
          const Icon = m.icon;
          const active = mode === m.value;
          return (
            <button
              key={m.value}
              onClick={() => setMode(m.value)}
              className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                active
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
              title={m.desc}
            >
              <Icon className="w-4 h-4" />
              {m.label}
            </button>
          );
        })}
      </div>

      {/* 对话区 — flex-1 占满剩余高度。key={mode}：切换模式时重挂，天然清空 messages 并 abort 旧 SSE */}
      <div className="flex-1 min-h-0">
        <AiChatPanel key={mode} mode={mode} />
      </div>
    </div>
  );
}

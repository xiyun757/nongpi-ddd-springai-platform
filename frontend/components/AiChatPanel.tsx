'use client';

import { useState, useRef, useCallback, useEffect } from 'react';
import { Send, Loader2 } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Button } from '@/components/ui/button';
import { AgentStepCard } from './AgentStepCard';
import { streamSse } from '@/lib/ai-sse';
import { fetchWithAuth, type AiMode } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';

interface Message {
  id: string;
  role: 'user' | 'assistant' | 'step' | 'complete' | 'error';
  content: string;
}

// 稳定的消息 id 生成器。React key 用 idx 会在消息追加时位置错乱（如
// 中途插入 error 消息导致后续 idx 偏移），用唯一 id 保证 key 稳定。
let msgSeq = 0;
const nextMsgId = () => `msg-${Date.now()}-${++msgSeq}`;

interface AiChatPanelProps {
  mode: AiMode;
}

/**
 * AI 对话面板 — 三种模式（chat/rag/manus）共用，SSE 流式 + 步骤卡片
 * 不引入 @microsoft/fetch-event-source，手写 streamSse 解析
 */
export function AiChatPanel({ mode }: AiChatPanelProps) {
  const { user } = useAuth();
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = useCallback(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, []);

  // chatId — 存 localStorage（按用户名+模式区分），刷新页面后复用同一 chatId，
  // 配合 Redis 持久化恢复历史对话（chat/rag 走 ChatMemory，manus 走独立 Redis key）
  const chatIdKey = `chatId:${user?.username ?? 'anon'}:${mode}`;
  const chatIdRef = useRef('');

  // 挂载时：从 localStorage 恢复 chatId + 从 Redis 拉历史对话恢复消息列表
  // 这是"从外部数据源恢复状态"的标准 effect 用法，非 set-state-in-effect 误用
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    const saved = localStorage.getItem(chatIdKey);
    if (!saved) return; // 无历史 chatId，首次发送时生成
    chatIdRef.current = saved;
    let cancelled = false;
    // manus 历史存独立 Redis key（manus:history:），chat/rag 走 ChatMemory
    const historyUrl = mode === 'manus'
      ? `/ai/manus/history?chatId=${saved}`
      : `/ai/history?chatId=${saved}`;
    fetchWithAuth(historyUrl)
      .then((res) => {
        // 区分"服务器错误"和"会话过期"。500/502 是临时错误（后端重启/Redis 抖动），
        // 保留 chatId 不清 localStorage，下次刷新可重试恢复。只有 200 且返回空数组才是
        // 真正的会话过期（Redis TTL 到期），才清 localStorage 重新开 chatId。
        if (!res.ok) return null; // 服务器错误 → 不处理，保留 saved chatId
        return res.json();
      })
      .then((hist: { role: string; content: string }[] | null) => {
        if (cancelled || hist === null) return; // 服务器错误或已卸载，保留 saved chatId
        if (hist.length === 0) {
          // Redis key 已过期（TTL 1h）→ 清 localStorage，下次发送重新生成 chatId
          localStorage.removeItem(chatIdKey);
          chatIdRef.current = '';
          return;
        }
        // Redis 有历史 → 恢复消息列表（manus 含 STEP，chat/rag 只 USER/ASSISTANT）
        const restored: Message[] = hist
          .filter((h) => h.role === 'USER' || h.role === 'ASSISTANT' || h.role === 'STEP')
          .map((h) => ({
            id: nextMsgId(),
            role: h.role === 'USER' ? ('user' as const)
              : h.role === 'STEP' ? ('step' as const)
              : ('assistant' as const),
            content: h.content,
          }));
        if (restored.length > 0) {
          setMessages(restored);
          setTimeout(scrollToBottom, 100);
        }
      })
      .catch(() => {
        // 网络错误（后端未启动）→ 保留 chatId，下次刷新重试。不清 localStorage。
        // token 失效由 fetchWithAuth 自动跳转登录页处理，无需在此清 chatId。
        if (cancelled) return;
      });
    return () => {
      cancelled = true;
    };
  }, [chatIdKey, mode, scrollToBottom]);
  /* eslint-enable react-hooks/set-state-in-effect */

  // 组件卸载时取消进行中的 SSE 请求，避免卸载后 setState 内存泄漏
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const handleSend = async () => {
    const text = input.trim();
    if (!text || loading) return;

    // 首次发送生成 chatId（本会话内复用，供后端 ChatMemory 串联多轮）
    if (!chatIdRef.current) {
      chatIdRef.current = `${user?.username ?? 'anon'}_${mode}_${Date.now()}`;
      localStorage.setItem(chatIdKey, chatIdRef.current);
    }

    setMessages((prev) => [...prev, { id: nextMsgId(), role: 'user', content: text }]);
    setInput('');
    setLoading(true);

    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    try {
      if (mode === 'rag') {
        // RAG 流式问答 — 与对话模式一致的逐字推送
        setMessages((prev) => [...prev, { id: nextMsgId(), role: 'assistant', content: '' }]);
        await streamSse(
          `/ai/chat/rag?message=${encodeURIComponent(text)}&chatId=${chatIdRef.current}`,
          { method: 'GET', signal: controller.signal },
          (evt) => {
            setMessages((prev) => {
              const last = prev[prev.length - 1];
              if (last?.role === 'assistant') {
                return [...prev.slice(0, -1), { ...last, content: last.content + evt.data }];
              }
              return prev;
            });
            setTimeout(scrollToBottom, 50);
          }
        );
      } else if (mode === 'manus') {
        // Manus ReAct 智能体 — SSE 推送 step/complete/error 事件
        await streamSse(
          `/ai/manus/chat?message=${encodeURIComponent(text)}&chatId=${chatIdRef.current}`,
          { method: 'GET', signal: controller.signal },
          (evt) => {
            if (evt.event === 'step') {
              setMessages((prev) => [...prev, { id: nextMsgId(), role: 'step', content: evt.data }]);
            } else if (evt.event === 'complete') {
              setMessages((prev) => [...prev, { id: nextMsgId(), role: 'complete', content: evt.data }]);
            } else if (evt.event === 'error') {
              setMessages((prev) => [...prev, { id: nextMsgId(), role: 'error', content: evt.data }]);
            } else if (!evt.event && evt.data) {
              // 无 event 行的兜底（Manus 不发，但兼容）
              setMessages((prev) => [...prev, { id: nextMsgId(), role: 'assistant', content: evt.data }]);
            }
            setTimeout(scrollToBottom, 50);
          }
        );
      } else {
        // 普通流式对话 — SSE chunk 逐字拼接
        setMessages((prev) => [...prev, { id: nextMsgId(), role: 'assistant', content: '' }]);
        await streamSse(
          `/ai/chat/stream?message=${encodeURIComponent(text)}&chatId=${chatIdRef.current}`,
          { method: 'GET', signal: controller.signal },
          (evt) => {
            // 普通流式：无 event 行，data 是文本片段
            setMessages((prev) => {
              const last = prev[prev.length - 1];
              if (last?.role === 'assistant') {
                return [...prev.slice(0, -1), { ...last, content: last.content + evt.data }];
              }
              return prev;
            });
            setTimeout(scrollToBottom, 50);
          }
        );
      }
    } catch (err) {
      // AbortError 是用户切换/卸载触发，静默
      if (err instanceof Error && err.name === 'AbortError') return;
      const msg = err instanceof Error ? err.message : '请求失败';
      setMessages((prev) => [...prev, { id: nextMsgId(), role: 'error', content: msg }]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const placeholder =
    mode === 'chat' ? '输入问题... (Enter 发送, Shift+Enter 换行)' :
    mode === 'rag' ? '问业务规范/流程，如：常温区干货能放多久？' :
    '描述多步任务，如：检查临期批次并冻结库存';

  return (
    <div className="flex flex-col h-full">
      {/* 消息列表 */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
        {messages.length === 0 && (
          <div className="text-center text-gray-400 py-12">
            <p className="text-sm">输入问题开始对话</p>
            {mode === 'manus' && (
              <p className="text-xs mt-1 text-gray-500">
                智能体会自动规划多步任务，逐步执行并展示 think/act 过程
              </p>
            )}
          </div>
        )}

        {messages.map((msg, idx) => {
          if (msg.role === 'user') {
            return (
              <div key={msg.id} className="flex justify-end">
                <div className="max-w-[80%] px-4 py-2 rounded-lg bg-blue-600 text-white text-sm whitespace-pre-wrap">
                  {msg.content}
                </div>
              </div>
            );
          }
          if (msg.role === 'step') {
            return <AgentStepCard key={msg.id} step={msg.content} />;
          }
          if (msg.role === 'complete') {
            return (
              <div key={msg.id} className="text-center text-xs text-green-600 py-2 font-medium">
                ✓ {msg.content}
              </div>
            );
          }
          if (msg.role === 'error') {
            return (
              <div key={msg.id} className="text-center text-xs text-red-500 py-2">
                ✗ {msg.content}
              </div>
            );
          }
          // assistant 气泡 — Markdown 渲染（RAG/Manus 回复含结构化内容）
          const isLastAssistant = idx === messages.length - 1 && loading;
          return (
            <div key={msg.id} className="flex justify-start">
              <div className="max-w-[80%] px-4 py-2 rounded-lg bg-gray-100 text-gray-800 text-sm">
                {msg.content ? (
                  <div className="ai-markdown">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                  </div>
                ) : (
                  isLastAssistant ? <span className="text-gray-400">思考中...</span> : null
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* 输入区 */}
      <div className="border-t border-gray-200 p-4 flex gap-2 items-end bg-white">
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          rows={2}
          className="flex-1 resize-none px-3 py-2 rounded-lg border border-gray-300 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          disabled={loading}
        />
        <Button onClick={handleSend} disabled={loading || !input.trim()} className="shrink-0">
          {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
        </Button>
      </div>
    </div>
  );
}

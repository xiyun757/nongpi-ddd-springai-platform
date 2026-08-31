import { getStoredToken, clearStoredAuth } from './auth-context';

/**
 * SSE 流式请求直连后端，绕过 Next.js rewrites 代理。
 * Next.js dev server 的 rewrites 用 http-proxy，会缓冲整个响应体，
 * 导致 SSE 流式请求无法逐 chunk 转发，前端一直收不到数据。
 * 所有 AI 端点（chat/stream、chat/rag、manus/chat）都走 SSE 直连。
 * 后端 CORS 已全部允许（SecurityConfig），直连无跨域问题。
 */
const SSE_API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080';

/**
 * SSE 事件结构（与后端 SseEmitter.event().name().data() 对应）
 */
export interface SseEvent {
  /** event: 行的值，未指定时为 undefined（普通流式 chunk） */
  event?: string;
  /** data: 行的值（多行 data 用 \n 拼接） */
  data: string;
}

/**
 * 流式 SSE 客户端 — 用 fetch + ReadableStream 手动解析，无第三方依赖。
 *
 * 浏览器原生 EventSource 只支持 GET 不能带 body/JWT，故自写。
 * 后端格式（与 AiController/BaseAgent 对齐）：
 *   - 普通流式：data:文本片段\n\n （无 event 行）
 *   - Manus：event:step\ndata:Step N: ...\n\n + event:complete + event:error
 */
export async function streamSse(
  url: string,
  options: {
    method?: string;
    body?: unknown;
    signal?: AbortSignal;
  } = {},
  onEvent: (evt: SseEvent) => void
): Promise<void> {
  const token = getStoredToken();
  const headers: Record<string, string> = {};
  if (options.body) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;

  // 直连后端，绕过 Next.js 代理（SSE 流式不被代理缓冲）
  const res = await fetch(SSE_API_BASE + '/api' + url, {
    method: options.method ?? 'POST',
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
  });

  if (!res.ok) {
    // 401: token 失效，与 fetchWithAuth 行为一致 — 清除认证 + 跳登录页
    if (res.status === 401 && typeof window !== 'undefined') {
      clearStoredAuth();
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
      return; // 页面即将跳转，不 throw 避免调用方显示无意义错误
    }
    const err = await res.json().catch(() => ({ error: `HTTP ${res.status}` }));
    throw new Error(err.error || `SSE 请求失败: HTTP ${res.status}`);
  }

  const reader = res.body?.getReader();
  if (!reader) throw new Error('ReadableStream 不可用（浏览器不支持流式响应）');

  const decoder = new TextDecoder();
  let buffer = '';
  let curEvent: string | undefined;
  let curData: string[] = [];
  // 后端 SseEmitter.complete() 关闭连接时，浏览器 fetch ReadableStream
  // 在跨域直连场景下会抛 TypeError("network error") — 这是连接关闭的副作用，
  // 不是真正的网络故障。已收到内容则视为正常结束静默退出。
  let receivedAny = false;

  // 标准按行解析，与浏览器原生 EventSource 行为一致。
  // 实测后端 SSE 字节流：data:<chunk>\n\n（每个事件含 \n\n 分隔符）
  // 每个 TCP chunk 可能只含部分数据（data: 或内容或 \n\n），必须用 buffer 累积按行分割。
  const flush = () => {
    if (curData.length > 0) {
      receivedAny = true;
      onEvent({ event: curEvent, data: curData.join('\n') });
    }
    curEvent = undefined;
    curData = [];
  };

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      // 归一化 \r\n 为 \n
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');

      // 按行分割，最后一行可能不完整（无 \n 结尾），保留到下次
      const lines = buffer.split('\n');
      buffer = lines.pop() ?? '';

      for (const line of lines) {
        if (line.startsWith('data:')) {
          curData.push(line.slice(5).replace(/^ /, ''));
        } else if (line.startsWith('event:')) {
          curEvent = line.slice(6).trim();
        } else if (line === '') {
          flush();
        }
      }
    }
    // 处理最后残留的不完整行
    if (buffer.startsWith('data:')) {
      curData.push(buffer.slice(5).replace(/^ /, ''));
    } else if (buffer.startsWith('event:')) {
      curEvent = buffer.slice(6).trim();
    }
    flush();
  } catch (e) {
    // 流尾连接关闭引发的 TypeError 视为正常结束
    if (receivedAny && e instanceof TypeError) return;
    throw e;
  }
}

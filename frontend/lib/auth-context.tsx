'use client';

import { createContext, useContext, useState, useEffect, ReactNode, useCallback } from 'react';

const TOKEN_KEY = 'auth_token';
const USER_KEY = 'auth_user';

export interface AuthUser {
  username: string;
  role: string;
}

interface AuthContextValue {
  token: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  loading: boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  // token/user 初始必须为 null，让 SSR 与客户端 hydration 首次渲染一致（避免 hydration mismatch）。
  // localStorage 只能在 effect 里读（挂载后），这正是 React 官方"客户端读外部存储"的标准模式；
  // 此处 set-state-in-effect 是该模式的预期用法，lint 规则为误报，故整段抑制。
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  /* eslint-disable react-hooks/set-state-in-effect -- 客户端读外部存储（localStorage）的标准模式，set-state-in-effect 在此为预期用法 */
  useEffect(() => {
    try {
      const t = localStorage.getItem(TOKEN_KEY);
      const u = localStorage.getItem(USER_KEY);
      if (t) setToken(t);
      if (u) setUser(JSON.parse(u));
    } catch {
      // localStorage 访问失败（SSR 或隐私模式）— 静默忽略
    } finally {
      setLoading(false);
    }
  }, []);
  /* eslint-enable react-hooks/set-state-in-effect */

  const login = useCallback(async (username: string, password: string) => {
    let res: Response;
    try {
      res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });
    } catch {
      // fetch 直接抛错 = 后端未启动 / 网络不通（next rewrite 代理 ECONNREFUSED）
      // 原代码兜底成"用户名或密码错误"严重误导，此处给出真实原因
      throw new Error('无法连接服务器，请确认后端已启动');
    }

    if (!res.ok) {
      // 仅 401 才是认证失败；其他状态码（500 代理错误等）不该说成密码错
      const err = await res.json().catch(() => ({}));
      if (res.status === 401) {
        throw new Error(err.error || '用户名或密码错误');
      }
      throw new Error(err.error || `服务异常（${res.status}），请稍后重试`);
    }

    const data = await res.json();
    const newUser: AuthUser = { username: data.username, role: data.role };

    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, JSON.stringify(newUser));

    setToken(data.token);
    setUser(newUser);
  }, []);

  const logout = useCallback(() => {
    // 清除聊天记录（按当前用户名清除对应 key，避免下个登录者看到历史）
    try {
      const rawUser = localStorage.getItem(USER_KEY);
      if (rawUser) {
        const u = JSON.parse(rawUser);
        if (u?.username) {
          const prefix = `chat_history_${u.username}_`;
          Object.keys(localStorage).forEach((k) => {
            if (k.startsWith(prefix)) localStorage.removeItem(k);
          });
        }
      }
    } catch {
      // 清除失败不阻断登出流程
    }
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setToken(null);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        token,
        user,
        isAuthenticated: !!token,
        login,
        logout,
        loading,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return ctx;
}

// 供 fetchWithAuth 使用的工具函数 — 不依赖 React Context，避免循环依赖
export function getStoredToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function clearStoredAuth() {
  if (typeof window === 'undefined') return;
  // 同时清除聊天记录（防止下一个登录者看到上一个账号的对话）
  try {
    const rawUser = localStorage.getItem(USER_KEY);
    if (rawUser) {
      const u = JSON.parse(rawUser);
      if (u?.username) {
        const prefix = `chat_history_${u.username}_`;
        Object.keys(localStorage).forEach((k) => {
          if (k.startsWith(prefix)) localStorage.removeItem(k);
        });
      }
    }
  } catch {}
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

'use client';

import { useState, FormEvent, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { showSuccess } from '@/lib/toast';

export default function LoginPage() {
  const router = useRouter();
  const { login, isAuthenticated, loading } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [shakeKey, setShakeKey] = useState(0);

  useEffect(() => {
    if (!loading && isAuthenticated) {
      // 登录后跳转仪表盘（根路由 /），不是批次管理页
      router.replace('/');
    }
  }, [loading, isAuthenticated, router]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setErrorMsg('');

    try {
      await login(username, password);
      showSuccess('登录成功');
      // 不在此处手动 router.replace — login() resolve 时 React 还未 re-render，
      // isAuthenticated 仍为 false，AuthGuard 会立即跳回 /login 形成跳登录竞态。
      // 上方 useEffect 监听 isAuthenticated 变化会自动跳转，无需重复。
    } catch (err) {
      const msg = err instanceof Error ? err.message : '用户名或密码错误';
      setErrorMsg(msg);
      setShakeKey((k) => k + 1);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex">
      {/* 左侧品牌区 — 低饱和度渐变 + logo + slogan */}
      <div
        className="hidden lg:flex lg:w-1/2 flex-col justify-between p-12 text-white relative overflow-hidden"
        style={{
          background:
            'linear-gradient(135deg, var(--primary) 0%, var(--primary) 99%)',
        }}
      >
        {/* 几何装饰 — 静态低饱和度色块，无动画 */}
        <div className="absolute top-10 right-10 w-40 h-40 rounded-full bg-white/5" />
        <div className="absolute bottom-20 left-10 w-56 h-56 rounded-full bg-white/5" />
        <div className="absolute top-1/2 right-1/4 w-24 h-24 rounded-2xl bg-white/5 rotate-12" />

        {/* Logo + 标题 */}
        <div className="relative z-10">
          <div className="flex items-center gap-3 mb-2">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              className="w-8 h-8"
            >
              <path d="M12 2L15 9H9L12 2Z" />
              <path d="M12 22L9 15H15L12 22Z" />
              <path d="M2 12L9 9V15L2 12Z" />
              <path d="M22 12L15 15V9L22 12Z" />
            </svg>
            <span className="text-lg font-semibold">农批履约中台</span>
          </div>
        </div>

        {/* Slogan */}
        <div className="relative z-10">
          <h2 className="text-3xl font-bold mb-3 leading-tight">
            AI 驱动的
            <br />
            农产品履约管理
          </h2>
          <p className="text-sm text-white/70 leading-relaxed max-w-md">
            基于 Spring AI + DDD 架构，集成 RAG 检索增强、Manus ReAct 智能体、
            FEFO 三重并发控制，覆盖批次全生命周期管理。
          </p>
        </div>

        {/* 底部技术标签 */}
        <div className="relative z-10 flex flex-wrap gap-2">
          {['Spring AI', 'DDD', 'RAG', 'Manus', 'Outbox', 'FEFO'].map((tag) => (
            <span
              key={tag}
              className="px-3 py-1 rounded-full text-xs bg-white/10 backdrop-blur-sm"
            >
              {tag}
            </span>
          ))}
        </div>
      </div>

      {/* 右侧表单区 */}
      <div className="flex-1 flex items-center justify-center px-6 py-12 bg-[#f5f5f5]">
        <div className="w-full max-w-sm animate-fade-in-scale">
          {/* 移动端 Logo（左侧隐藏时显示） */}
          <div className="lg:hidden flex items-center gap-2 justify-center mb-8">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="var(--primary)"
              strokeWidth="2"
              className="w-6 h-6"
            >
              <path d="M12 2L15 9H9L12 2Z" />
              <path d="M12 22L9 15H15L12 22Z" />
              <path d="M2 12L9 9V15L2 12Z" />
              <path d="M22 12L15 15V9L22 12Z" />
            </svg>
            <span className="text-base font-semibold text-gray-900">农批履约中台</span>
          </div>

          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-900">欢迎回来</h1>
            <p className="text-sm text-gray-500 mt-1">请使用管理员账号登录</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            {/* 用户名 */}
            <div className="login-input-group">
              <label className="block text-xs text-gray-500 mb-1.5">用户名</label>
              <div className="relative">
                <input
                  type="text"
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value);
                    if (errorMsg) setErrorMsg('');
                  }}
                  placeholder="admin"
                  autoFocus
                  required
                  className="w-full h-11 rounded-lg border border-gray-300 bg-white px-3.5 text-sm outline-none focus:border-[var(--primary)] focus:ring-2 focus:ring-[var(--primary)]/20 transition-all"
                />
                <span className="login-input-bar" />
              </div>
            </div>

            {/* 密码 */}
            <div className="login-input-group">
              <label className="block text-xs text-gray-500 mb-1.5">密码</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (errorMsg) setErrorMsg('');
                  }}
                  placeholder="••••••"
                  required
                  className="w-full h-11 rounded-lg border border-gray-300 bg-white px-3.5 pr-11 text-sm outline-none focus:border-[var(--primary)] focus:ring-2 focus:ring-[var(--primary)]/20 transition-all"
                />
                <span className="login-input-bar" />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
                  tabIndex={-1}
                >
                  {showPassword ? (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                      <line x1="1" y1="1" x2="23" y2="23" />
                    </svg>
                  ) : (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                      <circle cx="12" cy="12" r="3" />
                    </svg>
                  )}
                </button>
              </div>
            </div>

            {/* 错误提示 — 下滑展开 + 抖动 */}
            {errorMsg && (
              <div
                key={shakeKey}
                className="animate-slide-down animate-error-shake flex items-center gap-2 px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-sm text-red-600"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="12" />
                  <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
                {errorMsg}
              </div>
            )}

            {/* 登录按钮 — hover 时箭头右移 */}
            <button
              type="submit"
              disabled={submitting || !username || !password}
              className="group w-full h-11 rounded-lg bg-[var(--primary)] text-white font-medium text-sm flex items-center justify-center gap-2 hover:bg-[var(--primary)]/90 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
            >
              {submitting ? (
                <>
                  <svg className="animate-spin w-4 h-4" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  登录中...
                </>
              ) : (
                <>
                  登录
                  <svg
                    className="w-4 h-4 transition-transform group-hover:translate-x-1"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2.5"
                  >
                    <line x1="5" y1="12" x2="19" y2="12" />
                    <polyline points="12 5 19 12 12 19" />
                  </svg>
                </>
              )}
            </button>
          </form>

          <p className="mt-6 text-xs text-center text-gray-400">
            默认账号 admin / admin123
          </p>
        </div>
      </div>
    </div>
  );
}

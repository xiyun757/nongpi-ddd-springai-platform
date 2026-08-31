'use client';

import { useEffect, ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';

/**
 * 路由守卫 — 未登录跳 /login
 *
 * <p>使用方式：在需要登录的页面外层包裹：
 * <pre>{@code
 * <AuthGuard>
 *   <LotsPage />
 * </AuthGuard>
 * }</pre>
 * </p>
 *
 * <p>loading 期间渲染空白占位，避免渲染未授权内容闪烁。</p>
 */
export default function AuthGuard({ children }: { children: ReactNode }) {
  const { isAuthenticated, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !isAuthenticated) {
      router.replace('/login');
    }
  }, [loading, isAuthenticated, router]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center text-muted-foreground text-sm">
        加载中...
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="min-h-screen flex items-center justify-center text-muted-foreground text-sm">
        正在跳转登录...
      </div>
    );
  }

  return <>{children}</>;
}

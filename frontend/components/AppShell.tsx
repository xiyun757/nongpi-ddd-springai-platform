'use client';

import { ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import Sidebar from '@/components/Sidebar';

/**
 * 应用外壳 — 根据路径决定是否显示 Sidebar
 *
 * <p>/login 页面不显示 Sidebar（保持全屏居中布局），
 * 其余页面均显示左侧 Sidebar + 右侧内容区。</p>
 */
export default function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const isLogin = pathname.startsWith('/login');

  if (isLogin) {
    return <>{children}</>;
  }

  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <main className="flex-1 ml-64 p-6 w-[calc(100%-16rem)]">
        {/* key={pathname}：路由切换时强制重挂载，重放淡入动画（0.28s，全局生效） */}
        <div key={pathname} className="animate-page-fade">
          {children}
        </div>
      </main>
    </div>
  );
}

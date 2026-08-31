'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { Button } from '@/components/ui/button';

interface NavItem {
  href: string;
  icon: string;
  label: string;
  match: (path: string) => boolean;
  children?: NavItem[];
}

const NAV_ITEMS: NavItem[] = [
  { href: '/', icon: '📊', label: '仪表盘', match: (p) => p === '/' },
  { href: '/lots', icon: '📦', label: '批次管理', match: (p) => p.startsWith('/lots') },
  { href: '/inventory', icon: '📋', label: '库存管理', match: (p) => p.startsWith('/inventory') },
  {
    href: '/alerts',
    icon: '🔔',
    label: '预警管理',
    match: (p) => p === '/alerts',
    children: [
      { href: '/alerts/rules', icon: '⚙️', label: '规则管理', match: (p) => p === '/alerts/rules' },
    ],
  },
  { href: '/ai', icon: '🤖', label: 'AI 助手', match: (p) => p.startsWith('/ai') },
];

export default function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const { user, logout } = useAuth();

  const handleLogout = () => {
    logout();
    router.replace('/login');
  };

  return (
    <aside className="fixed left-0 top-0 z-40 w-64 min-h-screen bg-gray-50 border-r border-gray-200 flex flex-col">
      {/* Logo 区 */}
      <div className="px-6 py-5 border-b border-gray-200">
        <h1 className="text-base font-semibold text-gray-900">农批履约中台</h1>
        <p className="text-xs text-gray-500 mt-0.5">Fulfillment Platform</p>
      </div>

      {/* 导航菜单 — 扁平化列表 */}
      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        {NAV_ITEMS.reduce((acc: NavItem[], item) => {
          acc.push(item);
          if (item.children) {
            item.children.forEach((child) => acc.push(child));
          }
          return acc;
        }, []).map((item) => {
          const active = item.match(pathname);
          return (
            <Link
              key={item.label}
              href={item.href}
              className={`group flex items-center h-10 w-full pl-4 rounded-md text-sm font-medium transition-colors ${
                active
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-700 hover:bg-gray-100 hover:text-gray-900'
              }`}
            >
              <span className="shrink-0 flex items-center justify-center w-5 h-5 mr-3 text-base">
                {item.icon}
              </span>
              <span className="truncate">{item.label}</span>
            </Link>
          );
        })}
      </nav>

      {/* 底部用户区 */}
      <div className="border-t border-gray-200 p-4 mt-auto">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 min-w-0">
            <div className="w-8 h-8 rounded-full bg-blue-600 text-white text-xs font-medium flex items-center justify-center shrink-0">
              {user?.username?.charAt(0).toUpperCase() ?? 'U'}
            </div>
            <div className="min-w-0">
              <div className="text-sm font-medium text-gray-900 truncate">
                {user?.username ?? '未登录'}
              </div>
              <div className="text-xs text-gray-500">
                {user?.role ?? ''}
              </div>
            </div>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleLogout}
            className="text-gray-600 hover:text-red-600"
          >
            退出
          </Button>
        </div>
      </div>
    </aside>
  );
}

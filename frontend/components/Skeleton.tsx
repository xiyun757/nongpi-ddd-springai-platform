'use client';

/**
 * 通用骨架屏组件
 *
 * <p>统一所有页面的加载态视觉风格：灰色块 + Tailwind animate-pulse 脉冲动画。
 * 不含任何文字，仅形状。配合 TanStack React Query 的 isLoading / isInitialLoading 使用。</p>
 */

/** 基础骨架块 */
export function SkeletonBlock({ className = '' }: { className?: string }) {
  return (
    <div
      className={`bg-gray-200 rounded-md ${className}`}
      aria-hidden="true"
    />
  );
}

/** 骨架容器：自动应用 animate-pulse 与 space-y-4 */
export function SkeletonContainer({
  children,
  className = '',
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={`animate-pulse space-y-4 ${className}`}>{children}</div>
  );
}

/** 骨架卡片：模拟 shadcn Card（标题条 + rows 个内容条） */
export function SkeletonCard({
  rows = 3,
  className = '',
}: {
  rows?: number;
  className?: string;
}) {
  return (
    <div
      className={`animate-pulse bg-white rounded-lg shadow-sm ring-1 ring-gray-200 p-4 ${className}`}
      aria-hidden="true"
    >
      <SkeletonBlock className="h-5 w-32 mb-3" />
      <div className="space-y-2">
        {Array.from({ length: rows }).map((_, i) => (
          <SkeletonBlock
            key={i}
            className={i === rows - 1 ? 'h-4 w-2/3' : 'h-4 w-full'}
          />
        ))}
      </div>
    </div>
  );
}

/** 骨架表格：模拟 shadcn Table */
export function SkeletonTable({
  columns = 5,
  rows = 5,
}: {
  columns?: number;
  rows?: number;
}) {
  return (
    <div
      className="animate-pulse bg-white rounded-lg shadow-sm ring-1 ring-gray-200 overflow-hidden"
      aria-hidden="true"
    >
      <div className="p-4 border-b border-gray-100">
        <SkeletonBlock className="h-5 w-32" />
      </div>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr>
              {Array.from({ length: columns }).map((_, c) => (
                <th key={c} className="px-4 py-3">
                  <SkeletonBlock className="h-4 w-full" />
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: rows }).map((_, r) => (
              <tr key={r} className="border-t border-gray-100">
                {Array.from({ length: columns }).map((_, c) => (
                  <td key={c} className="px-4 py-3">
                    <SkeletonBlock
                      className={c === 0 ? 'h-4 w-16' : 'h-4 w-full'}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** 骨架指标卡：模拟仪表盘 StatCard */
export function SkeletonStatCard() {
  return (
    <div
      className="animate-pulse bg-white rounded-lg shadow-sm ring-1 ring-gray-200 p-4"
      aria-hidden="true"
    >
      <SkeletonBlock className="h-4 w-24 mb-3" />
      <SkeletonBlock className="h-8 w-20 mb-2" />
      <SkeletonBlock className="h-3 w-16" />
    </div>
  );
}

/** 骨架详情页：左 60% + 右 40% 双卡片布局 */
export function SkeletonDetailPage() {
  return (
    <div className="animate-pulse space-y-4" aria-hidden="true">
      <SkeletonBlock className="h-8 w-72" />
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-4">
        <div className="lg:col-span-3">
          <SkeletonCard rows={5} />
        </div>
        <div className="lg:col-span-2">
          <SkeletonCard rows={4} />
        </div>
      </div>
    </div>
  );
}

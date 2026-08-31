'use client';

import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import gsap from 'gsap';
import AuthGuard from '@/components/AuthGuard';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '@/components/ui/card';
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from '@/components/ui/table';
import {
  PieChart,
  Pie,
  Cell,
  Legend,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import {
  fetchTotalLots,
  fetchTotalInventoryQty,
  fetchPendingAlerts,
  fetchInventoryForChart,
  fetchTop5Alerts,
  TEMP_ZONE_LABEL,
} from '@/lib/api';
import {
  SkeletonStatCard,
  SkeletonCard,
  SkeletonContainer,
} from '@/components/Skeleton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { StatusBadge, ALERT_LEVEL_BADGE } from '@/components/StatusBadge';

const TEMP_ZONE_COLOR: Record<string, string> = {
  FREEZE: '#3b82f6',
  FRESH: '#10b981',
  NORMAL: '#f59e0b',
};

/**
 * 数字滚动动画 — GSAP 从 0 滚到目标值
 * 4 个防护
 * 1. kill 旧动画防叠加：effect 重新执行时先 killTweensOf
 * 2. hasInitialized 防重复：首次加载已渲染目标值，不重复触发滚动
 * 3. NaN 防护：Number.isFinite 校验，非法值直接显示 0
 * 4. 数据未变不重跑：目标值相等则跳过，避免 react-query 缓存命中重复动画
 */
function Counter({
  value,
  className,
}: {
  value: number;
  className?: string;
}) {
  const spanRef = useRef<HTMLSpanElement>(null);
  const displayRef = useRef(0);
  const hasInitialized = useRef(false);

  const target = Number.isFinite(value) ? value : 0;

  useEffect(() => {
    if (!spanRef.current) return;
    if (hasInitialized.current && displayRef.current === target) return;
    hasInitialized.current = true;

    const tween = gsap.to(displayRef, {
      current: target,
      duration: 0.8,
      ease: 'power1.out',
      onUpdate: () => {
        if (spanRef.current) {
          spanRef.current.textContent = Math.round(displayRef.current).toLocaleString();
        }
      },
    });
    return () => {
      gsap.killTweensOf(displayRef);
      tween.kill();
    };
  }, [target]);

  return <span ref={spanRef} className={className}>0</span>;
}

function StatCard({
  label,
  value,
  icon,
  tone = 'default',
}: {
  label: string;
  value: string | number;
  icon: string;
  tone?: 'default' | 'alert';
}) {
  const num = typeof value === 'number' ? value : Number(value);
  return (
    <Card className="transition-shadow duration-200 hover:shadow-md">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm font-medium text-muted-foreground">
            {label}
          </CardTitle>
          <span className={`h-9 w-9 rounded-lg flex items-center justify-center text-lg ${tone === 'alert' ? 'bg-red-50' : 'bg-blue-50'}`}>
            {icon}
          </span>
        </div>
      </CardHeader>
      <CardContent>
        <div
          className={`text-3xl font-bold ${
            tone === 'alert' && num > 0 ? 'text-red-600' : 'text-gray-900'
          }`}
        >
          <Counter value={num} />
        </div>
      </CardContent>
    </Card>
  );
}

function DashboardInner() {
  const { data: totalLots, isLoading: l1 } = useQuery({
    queryKey: ['stats', 'totalLots'],
    queryFn: fetchTotalLots,
  });

  const { data: totalInventory, isLoading: l2 } = useQuery({
    queryKey: ['stats', 'totalInventory'],
    queryFn: fetchTotalInventoryQty,
  });

  const { data: pendingAlerts, isLoading: l3 } = useQuery({
    queryKey: ['stats', 'pendingAlerts'],
    queryFn: fetchPendingAlerts,
  });

  const { data: inventory, isLoading: l4 } = useQuery({
    queryKey: ['inventory', 'chart'],
    queryFn: fetchInventoryForChart,
  });

  const { data: topAlerts, isLoading: l5 } = useQuery({
    queryKey: ['alerts', 'top5'],
    queryFn: fetchTop5Alerts,
  });

  // 饼图 hover 高亮 — 跟踪激活扇区索引，未悬停时 undefined（Recharts 不渲染 activeShape）
  const [activeIndex, setActiveIndex] = useState<number | null>(null);

  // 聚合温区分布
  const zoneData = (() => {
    if (!inventory || inventory.length === 0) return [];
    const m = new Map<string, number>();
    for (const r of inventory) {
      m.set(r.tempZone, (m.get(r.tempZone) ?? 0) + (r.totalQty || 0));
    }
    return Array.from(m.entries()).map(([tempZone, qty]) => ({
      name: TEMP_ZONE_LABEL[tempZone] ?? tempZone,
      value: qty,
      color: TEMP_ZONE_COLOR[tempZone] ?? '#94a3b8',
    }));
  })();

  const anyLoading = l1 || l2 || l3 || l4 || l5;

  if (anyLoading && !totalLots && !totalInventory && !pendingAlerts) {
    // 首屏骨架屏
    return (
      <div>
        <PageHeader title="仪表盘" subtitle="农批履约中台核心指标概览" />
        <SkeletonContainer className="mb-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <SkeletonStatCard />
            <SkeletonStatCard />
            <SkeletonStatCard />
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <SkeletonCard rows={4} />
            <SkeletonCard rows={4} />
          </div>
        </SkeletonContainer>
      </div>
    );
  }

  return (
    <div className="animate-fadeIn">
      <PageHeader title="仪表盘" subtitle="农批履约中台核心指标概览" />

      {/* 指标卡 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
        <StatCard
          label="在库批次总数"
          value={totalLots ?? 0}
          icon="📦"
        />
        <StatCard
          label="总库存量 (kg)"
          value={totalInventory ?? 0}
          icon="📊"
        />
        <StatCard
          label="待处理预警"
          value={pendingAlerts ?? 0}
          icon="🔔"
          tone="alert"
        />
      </div>

      {/* 两列布局 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 温区分布饼图 */}
        <Card className="transition-shadow duration-200 hover:shadow-md">
          <CardHeader>
            <CardTitle className="text-base font-semibold">温区库存分布</CardTitle>
          </CardHeader>
          <CardContent>
            {zoneData.length === 0 ? (
              <div className="h-[300px] flex items-center justify-center">
                <EmptyState icon="📊" message="暂无库存数据" />
              </div>
            ) : (
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={zoneData}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    outerRadius={90}
                    onMouseEnter={(_, i) => setActiveIndex(i)}
                    onMouseLeave={() => setActiveIndex(null)}
                    label={(entry: { name?: string; percent?: number }) =>
                      `${entry.name} ${((entry.percent ?? 0) * 100).toFixed(1)}%`
                    }
                  >
                    {zoneData.map((entry, i) => (
                      <Cell
                        key={i}
                        fill={entry.color}
                        fillOpacity={activeIndex === null || activeIndex === i ? 1 : 0.4}
                        stroke={activeIndex === i ? '#fff' : 'none'}
                        strokeWidth={activeIndex === i ? 2 : 0}
                        style={{ transition: 'fill-opacity 0.15s ease-out' }}
                      />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(v) => `${v ?? 0} kg`}
                  />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>

        {/* 待处理预警 Top5 */}
        <Card className="transition-shadow duration-200 hover:shadow-md">
          <CardHeader>
            <CardTitle className="text-base font-semibold">待处理预警 Top5</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow className="bg-gray-50">
                  <TableHead className="px-3">级别</TableHead>
                  <TableHead className="px-3">批次号</TableHead>
                  <TableHead className="px-3">消息</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(!topAlerts || topAlerts.length === 0) && (
                  <TableRow>
                    <TableCell colSpan={3}>
                      <EmptyState icon="🔔" message="暂无待处理预警" />
                    </TableCell>
                  </TableRow>
                )}
                {topAlerts?.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell className="px-3">
                      <StatusBadge variant={(ALERT_LEVEL_BADGE[a.alertLevel] ?? { variant: 'gray' }).variant}>
                        {ALERT_LEVEL_BADGE[a.alertLevel]?.label ?? a.alertLevel}
                      </StatusBadge>
                    </TableCell>
                    <TableCell className="px-3">
                      <span className="font-mono text-xs text-gray-700">
                        {a.lotNo}
                      </span>
                    </TableCell>
                    <TableCell className="px-3 text-xs text-gray-600 max-w-[200px]">
                      <span className="line-clamp-2">{a.message}</span>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  return (
    <AuthGuard>
      <DashboardInner />
    </AuthGuard>
  );
}

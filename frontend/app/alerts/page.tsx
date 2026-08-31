'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import AuthGuard from '@/components/AuthGuard';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from '@/components/ui/table';
import {
  fetchAlerts,
} from '@/lib/api';
import HandleAlertDialog from '@/components/HandleAlertDialog';
import { SkeletonTable } from '@/components/Skeleton';
import { formatDateTime } from '@/lib/utils';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { StatusBadge, ALERT_LEVEL_BADGE } from '@/components/StatusBadge';

type HandledFilter = 'all' | 'unhandled' | 'handled';
type LevelFilter = 'all' | 'CRITICAL' | 'WARNING' | 'INFO';

const HANDLED_OPTIONS: { value: HandledFilter; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'unhandled', label: '未处理' },
  { value: 'handled', label: '已处理' },
];

const LEVEL_OPTIONS: { value: LevelFilter; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'CRITICAL', label: '严重' },
  { value: 'WARNING', label: '警告' },
  { value: 'INFO', label: '提示' },
];

function AlertsPageInner() {
  const [handled, setHandled] = useState<HandledFilter>('unhandled');
  const [level, setLevel] = useState<LevelFilter>('all');
  const [activeId, setActiveId] = useState<number | null>(null);

  // 搜索与时间筛选
  const [searchInput, setSearchInput] = useState('');
  const [lotNo, setLotNo] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const { data, isLoading, error } = useQuery({
    queryKey: ['alerts', handled, level, lotNo, startDate, endDate],
    queryFn: () =>
      fetchAlerts({
        handled: handled === 'all' ? undefined : handled === 'handled',
        alertLevel: level === 'all' ? undefined : level,
        page: 1,
        size: 20,
        lotNo: lotNo || undefined,
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      }),
  });

  const handleSearch = () => {
    setLotNo(searchInput.trim());
  };

  const handleReset = () => {
    setSearchInput('');
    setLotNo('');
    setStartDate('');
    setEndDate('');
    setHandled('all');
    setLevel('all');
  };

  const records = data?.records ?? [];
  const activeAlert = activeId !== null ? records.find((r) => r.id === activeId) : null;

  return (
    <div className="animate-fadeIn">
      <PageHeader title="预警管理" subtitle="监控临期批次与库存异常" />

      {/* 筛选卡片 */}
      <Card className="mb-4">
        <CardContent className="pt-4 space-y-3">
          {/* 第一行：搜索框 + 时间范围 */}
          <div className="flex items-center gap-2 flex-wrap">
            <div className="relative flex-1 min-w-[200px] max-w-sm">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">🔍</span>
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') handleSearch(); }}
                placeholder="按批次号搜索"
                className="w-full border rounded pl-9 pr-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
              />
            </div>
            <div className="flex items-center gap-1">
              <span className="text-xs text-gray-500">起始</span>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="border rounded px-2 py-1.5 text-sm"
              />
            </div>
            <div className="flex items-center gap-1">
              <span className="text-xs text-gray-500">截止</span>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="border rounded px-2 py-1.5 text-sm"
              />
            </div>
            <Button size="sm" onClick={handleSearch}>搜索</Button>
            <Button size="sm" variant="outline" onClick={handleReset}>重置</Button>
          </div>

          {/* 第二行：状态 + 级别按钮 */}
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500 w-12">状态</span>
            <div className="flex gap-1">
              {HANDLED_OPTIONS.map((opt) => (
                <Button
                  key={opt.value}
                  size="sm"
                  variant={handled === opt.value ? 'default' : 'outline'}
                  onClick={() => setHandled(opt.value)}
                >
                  {opt.label}
                </Button>
              ))}
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500 w-12">级别</span>
            <div className="flex gap-1">
              {LEVEL_OPTIONS.map((opt) => (
                <Button
                  key={opt.value}
                  size="sm"
                  variant={level === opt.value ? 'default' : 'outline'}
                  onClick={() => setLevel(opt.value)}
                >
                  {opt.label}
                </Button>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 表格 */}
      <div className="bg-white rounded-lg shadow-sm ring-1 ring-gray-200 overflow-hidden">
        {isLoading ? (
          <SkeletonTable columns={7} rows={6} />
        ) : (
        <Table>
          <TableHeader>
            <TableRow className="bg-gray-50">
              <TableHead className="px-4">级别</TableHead>
              <TableHead className="px-4">批次号</TableHead>
              <TableHead className="px-4">预警消息</TableHead>
              <TableHead className="px-4">状态</TableHead>
              <TableHead className="px-4">处理人</TableHead>
              <TableHead className="px-4">创建时间</TableHead>
              <TableHead className="px-4 text-center">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {error && (
              <TableRow>
                <TableCell colSpan={7} className="text-center py-8 text-red-500">
                  加载失败，请重试
                </TableCell>
              </TableRow>
            )}

            {!error && records.length === 0 && (
              <TableRow>
                <TableCell colSpan={7}>
                  <EmptyState icon="🔔" message="暂无预警记录" description="当前筛选条件下没有预警" />
                </TableCell>
              </TableRow>
            )}

            {!error && records.map((r) => (
              <TableRow key={r.id} className="hover:bg-gray-50 transition-colors duration-150">
                <TableCell className="px-4">
                  <StatusBadge variant={(ALERT_LEVEL_BADGE[r.alertLevel] ?? { variant: 'gray' }).variant}>
                    {ALERT_LEVEL_BADGE[r.alertLevel]?.label ?? r.alertLevel}
                  </StatusBadge>
                </TableCell>
                <TableCell className="px-4 font-mono text-xs">{r.lotNo}</TableCell>
                <TableCell className="px-4 text-sm text-gray-700 max-w-xs">
                  <span className="line-clamp-2">{r.message}</span>
                </TableCell>
                <TableCell className="px-4">
                  {r.handled ? (
                    <StatusBadge variant="gray">已处理</StatusBadge>
                  ) : (
                    <StatusBadge variant="orange">未处理</StatusBadge>
                  )}
                </TableCell>
                <TableCell className="px-4 text-sm text-gray-600">
                  {r.handler ?? '-'}
                </TableCell>
                <TableCell className="px-4 text-xs text-gray-500">
                  {formatDateTime(r.createdAt)}
                </TableCell>
                <TableCell className="px-4 text-center">
                  {r.handled ? (
                    <span className="text-xs text-gray-400">已处理</span>
                  ) : (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setActiveId(r.id)}
                    >
                      处理
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        )}
      </div>

      {/* 记录数 */}
      <div className="mt-3 text-xs text-gray-500">
        共 {data?.total ?? 0} 条记录
      </div>

      {/* 处理 Dialog */}
      {activeAlert && (
        <HandleAlertDialog
          open
          onOpenChange={(o) => { if (!o) setActiveId(null); }}
          alertId={activeAlert.id}
          lotNo={activeAlert.lotNo}
          message={activeAlert.message}
        />
      )}
    </div>
  );
}

export default function AlertsPage() {
  return (
    <AuthGuard>
      <AlertsPageInner />
    </AuthGuard>
  );
}

'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
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
import { fetchInventoryList, InventoryItem } from '@/lib/api';
import InventoryAdjustDialog from '@/components/InventoryAdjustDialog';
import InventoryFreezeDialog from '@/components/InventoryFreezeDialog';
import { SkeletonTable } from '@/components/Skeleton';
import { formatDateTime } from '@/lib/utils';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { StatusBadge, TEMP_ZONE_BADGE } from '@/components/StatusBadge';

interface DialogState {
  type: 'adjust' | 'freeze' | 'unfreeze';
  item: InventoryItem;
}

function InventoryPageInner() {
  const [page, setPage] = useState(1);
  const size = 20;
  const [dialog, setDialog] = useState<DialogState | null>(null);

  // 筛选状态
  const [searchInput, setSearchInput] = useState('');
  const [skuId, setSkuId] = useState('');
  const [tempZone, setTempZone] = useState('');
  const [minQty, setMinQty] = useState('');
  const [maxQty, setMaxQty] = useState('');

  const skuIdNum = skuId ? Number(skuId) : undefined;
  const minQtyNum = minQty ? Number(minQty) : undefined;
  const maxQtyNum = maxQty ? Number(maxQty) : undefined;

  const { data, isLoading, error } = useQuery({
    queryKey: ['inventory', page, skuId, tempZone, minQty, maxQty],
    queryFn: () => fetchInventoryList({
      page,
      size,
      skuId: skuIdNum,
      tempZone: tempZone || undefined,
      minQty: minQtyNum,
      maxQty: maxQtyNum,
    }),
  });

  const handleSearch = () => {
    setPage(1);
    setSkuId(searchInput.trim());
  };

  const handleReset = () => {
    setSearchInput('');
    setSkuId('');
    setTempZone('');
    setMinQty('');
    setMaxQty('');
    setPage(1);
  };

  const closeDialog = () => setDialog(null);

  return (
    <div className="animate-fadeIn">
      <PageHeader
        title="库存管理"
        subtitle={`共 ${data?.total ?? 0} 条记录`}
      />

      {/* 筛选卡片 */}
      <Card className="mb-4">
        <CardContent className="pt-4 space-y-3">
          <div className="flex items-center gap-2">
            <div className="relative flex-1 max-w-sm">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">🔍</span>
              <input
                type="number"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') handleSearch(); }}
                placeholder="按 SKU ID 搜索"
                className="w-full border rounded pl-9 pr-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
              />
            </div>
            <Button size="sm" onClick={handleSearch}>搜索</Button>
            <Button size="sm" variant="outline" onClick={handleReset}>重置</Button>
          </div>
          <div className="grid grid-cols-3 gap-3 max-w-2xl">
            <div>
              <label className="block text-xs text-gray-500 mb-1">温区</label>
              <select
                value={tempZone}
                onChange={(e) => { setTempZone(e.target.value); setPage(1); }}
                className="border rounded px-2 py-1.5 w-full text-sm bg-white"
              >
                <option value="">全部</option>
                <option value="FREEZE">冷冻</option>
                <option value="FRESH">冷藏</option>
                <option value="NORMAL">常温</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">最小库存量</label>
              <input
                type="number"
                value={minQty}
                onChange={(e) => { setMinQty(e.target.value); setPage(1); }}
                placeholder="0"
                className="border rounded px-2 py-1.5 w-full text-sm"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">最大库存量</label>
              <input
                type="number"
                value={maxQty}
                onChange={(e) => { setMaxQty(e.target.value); setPage(1); }}
                placeholder="不限"
                className="border rounded px-2 py-1.5 w-full text-sm"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="bg-white rounded-lg shadow-sm ring-1 ring-gray-200 overflow-hidden">
        {isLoading ? (
          <SkeletonTable columns={7} rows={8} />
        ) : (
        <Table>
          <TableHeader>
            <TableRow className="bg-gray-50">
              <TableHead className="px-4">SKU ID</TableHead>
              <TableHead className="px-4">温区</TableHead>
              <TableHead className="px-4 text-right">总库存量 (kg)</TableHead>
              <TableHead className="px-4 text-right">冻结量 (kg)</TableHead>
              <TableHead className="px-4 text-right">可用量 (kg)</TableHead>
              <TableHead className="px-4">最后更新时间</TableHead>
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

            {!error && data?.records?.length === 0 && (
              <TableRow>
                <TableCell colSpan={7}>
                  <EmptyState message="暂无库存记录" description="尝试调整筛选条件" />
                </TableCell>
              </TableRow>
            )}

            {!error && data?.records?.map((item) => (
              <TableRow key={`${item.skuId}-${item.tempZone}`} className="hover:bg-gray-50 transition-colors duration-150">
                <TableCell className="px-4">
                  <Link
                    href={`/inventory/${item.skuId}/${item.tempZone}`}
                    className="text-blue-600 hover:underline cursor-pointer"
                  >
                    {item.skuId}
                  </Link>
                </TableCell>
                <TableCell className="px-4">
                  <StatusBadge variant={(TEMP_ZONE_BADGE[item.tempZone] ?? { variant: 'gray' }).variant}>
                    {TEMP_ZONE_BADGE[item.tempZone]?.label ?? item.tempZone}
                  </StatusBadge>
                </TableCell>
                <TableCell className="px-4 text-right font-medium">
                  {item.totalQty}
                </TableCell>
                <TableCell className="px-4 text-right text-amber-600">
                  {item.frozenQty}
                </TableCell>
                <TableCell className="px-4 text-right text-green-600 font-medium">
                  {item.availableQty}
                </TableCell>
                <TableCell className="px-4 text-gray-500 text-xs">
                  {formatDateTime(item.updatedAt)}
                </TableCell>
                <TableCell className="px-4">
                  <div className="flex items-center justify-center gap-1.5">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setDialog({ type: 'adjust', item })}
                    >
                      调整
                    </Button>
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => setDialog({ type: 'freeze', item })}
                    >
                      冻结
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setDialog({ type: 'unfreeze', item })}
                      disabled={item.frozenQty <= 0}
                    >
                      解冻
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        )}
      </div>

      {/* 调整 Dialog */}
      {dialog?.type === 'adjust' && (
        <InventoryAdjustDialog
          open
          onOpenChange={(o) => { if (!o) closeDialog(); }}
          skuId={dialog.item.skuId}
          tempZone={dialog.item.tempZone}
          currentQty={dialog.item.totalQty}
        />
      )}

      {/* 冻结 Dialog */}
      {dialog?.type === 'freeze' && (
        <InventoryFreezeDialog
          open
          onOpenChange={(o) => { if (!o) closeDialog(); }}
          skuId={dialog.item.skuId}
          tempZone={dialog.item.tempZone}
          currentFrozenQty={dialog.item.frozenQty}
          mode="freeze"
        />
      )}

      {/* 解冻 Dialog */}
      {dialog?.type === 'unfreeze' && (
        <InventoryFreezeDialog
          open
          onOpenChange={(o) => { if (!o) closeDialog(); }}
          skuId={dialog.item.skuId}
          tempZone={dialog.item.tempZone}
          currentFrozenQty={dialog.item.frozenQty}
          mode="unfreeze"
        />
      )}
    </div>
  );
}

export default function InventoryPage() {
  return (
    <AuthGuard>
      <InventoryPageInner />
    </AuthGuard>
  );
}

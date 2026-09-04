'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchLots, inbound, LotItem, fetchSkus } from '@/lib/api';
import { useState } from 'react';
import Link from 'next/link';
import AuthGuard from '@/components/AuthGuard';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import OutboundDialog from '@/components/OutboundDialog';
import { showActionSuccess, showActionError } from '@/lib/toast';
import { SkeletonTable } from '@/components/Skeleton';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { StatusBadge, LOT_STATUS_BADGE, TEMP_ZONE_BADGE } from '@/components/StatusBadge';

function LotsPageInner() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const size = 20;

  // 筛选状态
  const [searchInput, setSearchInput] = useState('');
  const [lotNo, setLotNo] = useState('');
  const [tempZone, setTempZone] = useState('');
  const [status, setStatus] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['lots', page, lotNo, tempZone, status],
    queryFn: () => fetchLots({ page, size, lotNo: lotNo || undefined, tempZone: tempZone || undefined, status: status || undefined }),
  });

  // 商品主数据下拉（入库表单选择商品，替代手填 skuId 数字）
  const { data: skus } = useQuery({
    queryKey: ['skus'],
    queryFn: fetchSkus,
  });

  const handleSearch = () => {
    setPage(1);
    setLotNo(searchInput.trim());
  };

  const handleReset = () => {
    setSearchInput('');
    setLotNo('');
    setTempZone('');
    setStatus('');
    setPage(1);
  };

  const [showForm, setShowForm] = useState(false);
  const [activeLot, setActiveLot] = useState<LotItem | null>(null);
  const [form, setForm] = useState({
    skuId: 0, // 提交时若为 0 则取商品列表第一个（下拉框默认选中）
    tempZone: 'FREEZE',
    produceDate: '',
    expireDate: '',
    qty: 100,
    supplierId: 1,
  });

  const inboundMut = useMutation({
    mutationFn: inbound,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['lots'] });
      setShowForm(false);
      showActionSuccess('入库', data.lotNo);
    },
    onError: (err: Error) => {
      showActionError('入库', err.message);
    },
  });

  return (
    <div className="animate-fadeIn">
      <PageHeader
        title="批次管理"
        actions={
          <Button size="sm" onClick={() => setShowForm(!showForm)}>
            {showForm ? '取消' : '新建入库'}
          </Button>
        }
      />

      {/* 筛选卡片 */}
      <Card className="mb-4">
        <CardContent className="pt-4 space-y-3">
          <div className="flex items-center gap-2">
            <div className="relative flex-1 max-w-sm">
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
            <Button size="sm" onClick={handleSearch}>搜索</Button>
            <Button size="sm" variant="outline" onClick={handleReset}>重置</Button>
          </div>
          <div className="grid grid-cols-2 gap-3 max-w-md">
            <div>
              <label className="block text-xs text-gray-500 mb-1">温区</label>
              <select
                value={tempZone}
                onChange={(e) => { setTempZone(e.target.value); setSearchInput(''); setLotNo(''); setPage(1); }}
                className="border rounded px-2 py-1.5 w-full text-sm bg-white"
              >
                <option value="">全部</option>
                <option value="FREEZE">冷冻</option>
                <option value="FRESH">冷藏</option>
                <option value="NORMAL">常温</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">状态</label>
              <select
                value={status}
                onChange={(e) => { setStatus(e.target.value); setSearchInput(''); setLotNo(''); setPage(1); }}
                className="border rounded px-2 py-1.5 w-full text-sm bg-white"
              >
                <option value="">全部</option>
                <option value="IN_STOCK">在库</option>
                <option value="PARTIAL_OUT">部分出库</option>
                <option value="FULLY_OUT">已出清</option>
                <option value="EXPIRED">已过期</option>
              </select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 入库表单 — 表单显示不依赖列表 loading，避免改筛选时点新建表单变骨架屏 */}
      {showForm && (
        <div className="bg-white p-4 rounded shadow mb-4 max-w-lg">
          <h2 className="text-base font-semibold mb-3">入库登记</h2>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-gray-500 mb-1">商品</label>
              <select
                value={form.skuId}
                onChange={(e) => setForm({ ...form, skuId: Number(e.target.value) })}
                className="border rounded px-2 py-1 w-full text-sm"
              >
                {!skus || skus.length === 0 ? (
                  <option value={0}>暂无商品，请先创建</option>
                ) : (
                  skus.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}（ID:{s.id}）
                    </option>
                  ))
                )}
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">温区</label>
              <select
                value={form.tempZone}
                onChange={(e) => setForm({ ...form, tempZone: e.target.value })}
                className="border rounded px-2 py-1 w-full text-sm"
              >
                <option value="FREEZE">冷冻</option>
                <option value="FRESH">冷藏</option>
                <option value="NORMAL">常温</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">生产日期</label>
              <input
                type="date"
                value={form.produceDate}
                onChange={(e) => setForm({ ...form, produceDate: e.target.value })}
                className="border rounded px-2 py-1 w-full text-sm"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">过期日期</label>
              <input
                type="date"
                value={form.expireDate}
                onChange={(e) => setForm({ ...form, expireDate: e.target.value })}
                className="border rounded px-2 py-1 w-full text-sm"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">数量</label>
              <input
                type="number"
                value={form.qty}
                onChange={(e) => setForm({ ...form, qty: Number(e.target.value) })}
                className="border rounded px-2 py-1 w-full text-sm"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">供应商 ID</label>
              <input
                type="number"
                value={form.supplierId}
                onChange={(e) => setForm({ ...form, supplierId: Number(e.target.value) })}
                className="border rounded px-2 py-1 w-full text-sm"
              />
            </div>
          </div>
          <button
            onClick={() => {
              // skuId 兜底：默认 0 时取商品列表第一个（下拉默认选中项）
              const payload = { ...form };
              if (payload.skuId === 0 && skus && skus.length > 0) {
                payload.skuId = skus[0].id;
              }
              inboundMut.mutate(payload);
            }}
            disabled={inboundMut.isPending || !form.produceDate || !form.expireDate || !skus || skus.length === 0}
            className="mt-3 px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 text-sm disabled:opacity-50"
          >
            {inboundMut.isPending ? '提交中...' : '确认入库'}
          </button>
        </div>
      )}

      {/* 表格 */}
      {isLoading ? (
        <SkeletonTable columns={7} rows={8} />
      ) : (
      <div className="bg-white rounded-lg shadow-sm ring-1 ring-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="text-left px-4 py-2">批次号</th>
              <th className="text-left px-4 py-2">商品</th>
              <th className="text-left px-4 py-2">温区</th>
              <th className="text-right px-4 py-2">初始量</th>
              <th className="text-right px-4 py-2">剩余量</th>
              <th className="text-left px-4 py-2">状态</th>
              <th className="text-center px-4 py-2">操作</th>
            </tr>
          </thead>
          <tbody>
            {data?.records?.map((lot) => (
              <tr key={lot.lotNo} className="border-t hover:bg-gray-50 transition-colors duration-150">
                <td className="px-4 py-2 font-mono text-xs">
                  <Link
                    href={`/lots/${lot.lotNo}`}
                    className="text-blue-600 hover:underline cursor-pointer"
                  >
                    {lot.lotNo}
                  </Link>
                </td>
                <td className="px-4 py-2">
                  {lot.skuName ?? (
                    <span className="text-gray-400">SKU {lot.skuId}</span>
                  )}
                </td>
                <td className="px-4 py-2">
                  <StatusBadge variant={(TEMP_ZONE_BADGE[lot.tempZone] ?? { variant: 'gray' }).variant}>
                    {TEMP_ZONE_BADGE[lot.tempZone]?.label ?? lot.tempZone}
                  </StatusBadge>
                </td>
                <td className="px-4 py-2 text-right">{lot.initialQty}</td>
                <td className="px-4 py-2 text-right">{lot.remainingQty}</td>
                <td className="px-4 py-2">
                  <StatusBadge variant={(LOT_STATUS_BADGE[lot.status] ?? { variant: 'gray' }).variant}>
                    {LOT_STATUS_BADGE[lot.status]?.label ?? lot.status}
                  </StatusBadge>
                </td>
                <td className="px-4 py-2 text-center">
                  <div className="flex items-center justify-center gap-1.5">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setActiveLot(lot)}
                      disabled={lot.remainingQty <= 0}
                    >
                      出库
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
            {!isLoading && data?.records?.length === 0 && (
              <tr>
                <td colSpan={7}>
                  <EmptyState message="暂无批次数据" description="点击右上角「新建入库」添加" />
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      )}


      {/* 分页 */}
      {data && data.pages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-4">
          <button
            onClick={() => setPage(page - 1)}
            disabled={page <= 1}
            className="px-3 py-1 border rounded text-sm disabled:opacity-40"
          >
            上一页
          </button>
          <span className="text-sm text-gray-500">
            {page} / {data.pages}
          </span>
          <button
            onClick={() => setPage(page + 1)}
            disabled={page >= data.pages}
            className="px-3 py-1 border rounded text-sm disabled:opacity-40"
          >
            下一页
          </button>
        </div>
      )}

      {/* 出库 Dialog */}
      {activeLot && (
        <OutboundDialog
          open
          onOpenChange={(o) => { if (!o) setActiveLot(null); }}
          lot={activeLot}
        />
      )}
    </div>
  );
}

export default function LotsPage() {
  return (
    <AuthGuard>
      <LotsPageInner />
    </AuthGuard>
  );
}

'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import AuthGuard from '@/components/AuthGuard';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { inbound } from '@/lib/api';
import { showActionSuccess, showActionError } from '@/lib/toast';

const TEMP_ZONE_OPTIONS = [
  { value: 'FREEZE', label: '冷冻 (-18°C)' },
  { value: 'FRESH', label: '冷藏 (0-4°C)' },
  { value: 'NORMAL', label: '常温' },
];

const inputCls =
  'w-full border rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400';

function NewLotPageInner() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [skuId, setSkuId] = useState('');
  const [tempZone, setTempZone] = useState('FREEZE');
  const [produceDate, setProduceDate] = useState('');
  const [expireDate, setExpireDate] = useState('');
  const [qty, setQty] = useState('');
  const [supplierId, setSupplierId] = useState('');

  const mutation = useMutation({
    mutationFn: inbound,
    onSuccess: (lot) => {
      showActionSuccess('入库');
      queryClient.invalidateQueries({ queryKey: ['lots'] });
      router.push(`/lots/${lot.lotNo}`);
    },
    onError: (err: Error) => {
      showActionError('入库', err.message || '请稍后重试');
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!skuId || !produceDate || !expireDate || !qty || !supplierId) {
      showActionError('入库', '请填写所有必填项');
      return;
    }
    if (expireDate <= produceDate) {
      showActionError('入库', '过期日期必须晚于生产日期');
      return;
    }
    mutation.mutate({
      skuId: Number(skuId),
      tempZone,
      produceDate,
      expireDate,
      qty: Number(qty),
      supplierId: Number(supplierId),
    });
  };

  return (
    <div className="animate-fadeIn">
      <div className="mb-4">
        <h1 className="text-xl font-bold text-gray-900">新建入库单</h1>
        <p className="text-sm text-gray-500 mt-1">登记新批次入库，系统自动生成批次号并同步库存</p>
      </div>

      <Card className="max-w-lg">
        <CardHeader>
          <CardTitle className="text-base font-semibold">批次信息</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-500 mb-1">SKU ID *</label>
                <input
                  type="number"
                  min={1}
                  value={skuId}
                  onChange={(e) => setSkuId(e.target.value)}
                  placeholder="如 1001"
                  className={inputCls}
                  required
                />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">供应商 ID *</label>
                <input
                  type="number"
                  min={1}
                  value={supplierId}
                  onChange={(e) => setSupplierId(e.target.value)}
                  placeholder="如 1"
                  className={inputCls}
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-xs text-gray-500 mb-1">温区 *</label>
              <select
                value={tempZone}
                onChange={(e) => setTempZone(e.target.value)}
                className={inputCls}
              >
                {TEMP_ZONE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-500 mb-1">生产日期 *</label>
                <input
                  type="date"
                  value={produceDate}
                  onChange={(e) => setProduceDate(e.target.value)}
                  className={inputCls}
                  required
                />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">过期日期 *</label>
                <input
                  type="date"
                  value={expireDate}
                  onChange={(e) => setExpireDate(e.target.value)}
                  className={inputCls}
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-xs text-gray-500 mb-1">入库数量 (kg) *</label>
              <input
                type="number"
                min={0.01}
                step={0.01}
                value={qty}
                onChange={(e) => setQty(e.target.value)}
                placeholder="如 100"
                className={inputCls}
                required
              />
            </div>

            <div className="flex gap-2 pt-2">
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? '提交中…' : '提交入库'}
              </Button>
              <Button type="button" variant="outline" onClick={() => router.push('/lots')}>
                取消
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

export default function NewLotPage() {
  return (
    <AuthGuard>
      <NewLotPageInner />
    </AuthGuard>
  );
}

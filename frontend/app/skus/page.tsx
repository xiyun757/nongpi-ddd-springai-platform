'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import AuthGuard from '@/components/AuthGuard';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { SkeletonTable } from '@/components/Skeleton';
import { showActionSuccess, showActionError } from '@/lib/toast';
import { fetchSkus, createSku, deleteSku, type SkuItem } from '@/lib/api';

function SkusPageInner() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ name: '', spec: '', unit: 'kg', barcode: '' });

  const { data: skus, isLoading } = useQuery({
    queryKey: ['skus'],
    queryFn: fetchSkus,
  });

  const createMut = useMutation({
    mutationFn: createSku,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skus'] });
      setShowForm(false);
      setForm({ name: '', spec: '', unit: 'kg', barcode: '' });
      showActionSuccess('新建商品');
    },
    onError: (err: Error) => showActionError('新建商品', err.message),
  });

  const deleteMut = useMutation({
    mutationFn: deleteSku,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skus'] });
      showActionSuccess('删除商品');
    },
    onError: (err: Error) => showActionError('删除商品', err.message),
  });

  const handleSubmit = () => {
    if (!form.name.trim() || !form.unit.trim()) {
      showActionError('新建商品', '商品名称和计量单位不能为空');
      return;
    }
    createMut.mutate({
      name: form.name.trim(),
      spec: form.spec.trim() || undefined,
      unit: form.unit.trim(),
      barcode: form.barcode.trim() || undefined,
    });
  };

  const handleDelete = (sku: SkuItem) => {
    if (!confirm(`确认删除商品「${sku.name}」？\n注意：被批次引用的商品可能无法删除。`)) return;
    deleteMut.mutate(sku.id);
  };

  return (
    <div className="animate-fadeIn">
      <PageHeader
        title="商品管理"
        actions={
          <Button size="sm" onClick={() => setShowForm(!showForm)}>
            {showForm ? '取消' : '新建商品'}
          </Button>
        }
      />

      {showForm && (
        <div className="bg-white p-4 rounded shadow mb-4 max-w-lg">
          <h2 className="text-base font-semibold mb-3">新建商品</h2>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-gray-500 mb-1">商品名称 *</label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="如：大白菜"
                className="w-full border rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">计量单位 *</label>
              <input
                type="text"
                value={form.unit}
                onChange={(e) => setForm({ ...form, unit: e.target.value })}
                placeholder="如：kg"
                className="w-full border rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">规格</label>
              <input
                type="text"
                value={form.spec}
                onChange={(e) => setForm({ ...form, spec: e.target.value })}
                placeholder="如：一级/10kg/箱"
                className="w-full border rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">条码</label>
              <input
                type="text"
                value={form.barcode}
                onChange={(e) => setForm({ ...form, barcode: e.target.value })}
                placeholder="可选"
                className="w-full border rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
              />
            </div>
          </div>
          <button
            onClick={handleSubmit}
            disabled={createMut.isPending || !form.name.trim() || !form.unit.trim()}
            className="mt-3 px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 text-sm disabled:opacity-50"
          >
            {createMut.isPending ? '提交中...' : '确认新建'}
          </button>
        </div>
      )}

      {isLoading ? (
        <SkeletonTable columns={5} rows={5} />
      ) : (
        <div className="bg-white rounded-lg shadow-sm ring-1 ring-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-4 py-2">ID</th>
                <th className="text-left px-4 py-2">商品名称</th>
                <th className="text-left px-4 py-2">规格</th>
                <th className="text-left px-4 py-2">单位</th>
                <th className="text-left px-4 py-2">条码</th>
                <th className="text-center px-4 py-2">操作</th>
              </tr>
            </thead>
            <tbody>
              {skus?.map((sku) => (
                <tr key={sku.id} className="border-t hover:bg-gray-50 transition-colors duration-150">
                  <td className="px-4 py-2 text-gray-500">{sku.id}</td>
                  <td className="px-4 py-2 font-medium text-gray-900">{sku.name}</td>
                  <td className="px-4 py-2 text-gray-600">{sku.spec || '-'}</td>
                  <td className="px-4 py-2 text-gray-600">{sku.unit}</td>
                  <td className="px-4 py-2 text-gray-400 font-mono text-xs">{sku.barcode || '-'}</td>
                  <td className="px-4 py-2 text-center">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => handleDelete(sku)}
                      disabled={deleteMut.isPending}
                      className="text-red-600 hover:text-red-700"
                    >
                      删除
                    </Button>
                  </td>
                </tr>
              ))}
              {skus && skus.length === 0 && (
                <tr>
                  <td colSpan={6}>
                    <EmptyState message="暂无商品" description="点击右上角「新建商品」添加" />
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default function SkusPage() {
  return (
    <AuthGuard>
      <SkusPageInner />
    </AuthGuard>
  );
}
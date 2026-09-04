'use client';

import { useQuery } from '@tanstack/react-query';
import { useParams, useRouter } from 'next/navigation';
import AuthGuard from '@/components/AuthGuard';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
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
  fetchLotDetail,
  fetchLotTransfers,
  TEMP_ZONE_LABEL,
  STATUS_LABEL,
  STATUS_COLOR,
  TransferRecord,
} from '@/lib/api';
import { formatDateTime } from '@/lib/utils';
import { SkeletonDetailPage } from '@/components/Skeleton';
import TransferDialog from '@/components/TransferDialog';
import { useState } from 'react';

const TRANSFER_TYPE_LABEL: Record<string, string> = {
  INBOUND: '入库',
  OUTBOUND: '出库',
  TRANSFER: '转库',
};

const TRANSFER_TYPE_COLOR: Record<string, string> = {
  INBOUND: 'bg-green-100 text-green-700',
  OUTBOUND: 'bg-red-100 text-red-700',
  TRANSFER: 'bg-blue-100 text-blue-700',
};

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between py-2 border-b border-gray-100 last:border-b-0">
      <span className="text-xs text-gray-500">{label}</span>
      <span className="text-sm text-gray-900 font-medium">{value ?? '-'}</span>
    </div>
  );
}

function LotDetailPageInner() {
  const params = useParams();
  const router = useRouter();
  const lotNo = String(params?.lotNo ?? '');
  const [showTransfer, setShowTransfer] = useState(false);

  const detailQuery = useQuery({
    queryKey: ['lot-detail', lotNo],
    queryFn: () => fetchLotDetail(lotNo),
    enabled: !!lotNo,
    retry: false,
  });

  const transfersQuery = useQuery({
    queryKey: ['lot-transfers', lotNo],
    queryFn: () => fetchLotTransfers(lotNo),
    enabled: !!lotNo,
    retry: false,
  });

  // 加载中：骨架屏
  if (detailQuery.isLoading) {
    return (
      <div>
        <SkeletonDetailPage />
        <BackButton onClick={() => router.push('/lots')} />
      </div>
    );
  }

  // 加载失败 / 批次不存在
  if (detailQuery.isError || !detailQuery.data) {
    const msg = detailQuery.error instanceof Error ? detailQuery.error.message : '';
    const isNotFound = msg.includes('不存在') || msg.includes('404');
    return (
      <div>
        <div className="bg-white rounded-lg shadow-sm ring-1 ring-gray-200 p-8 text-center">
          <p className="text-base font-medium text-gray-700">
            {isNotFound ? '批次不存在' : '加载失败，请重试'}
          </p>
          {msg && !isNotFound && (
            <p className="text-xs text-red-500 mt-2">{msg}</p>
          )}
        </div>
        <BackButton onClick={() => router.push('/lots')} />
      </div>
    );
  }

  const lot = detailQuery.data;
  const usedQty = (lot.initialQty ?? 0) - (lot.remainingQty ?? 0);
  const transfers = transfersQuery.data ?? [];

  return (
    <div className="animate-fadeIn">
      {/* 顶部：批次号 + 状态 + 操作 */}
      <div className="flex items-center gap-3 mb-4">
        <h1 className="text-2xl font-bold text-gray-900 font-mono">{lot.lotNo}</h1>
        <span
          className={`px-2 py-0.5 rounded text-xs font-medium ${
            STATUS_COLOR[lot.status] ?? 'bg-gray-100 text-gray-600'
          }`}
        >
          {STATUS_LABEL[lot.status] ?? lot.status}
        </span>
        <div className="flex-1" />
        <Button
          size="sm"
          variant="outline"
          onClick={() => setShowTransfer(true)}
          disabled={(lot.remainingQty ?? 0) <= 0}
        >
          转库
        </Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-4">
        {/* 左侧：基本信息 60% */}
        <div className="lg:col-span-3">
          <Card>
            <CardHeader>
              <CardTitle>基本信息</CardTitle>
              <CardDescription>批次的元数据与库存状态</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 gap-x-6">
                <Field label="SKU ID" value={lot.skuId} />
                <Field
                  label="温区"
                  value={TEMP_ZONE_LABEL[lot.tempZone] ?? lot.tempZone}
                />
                <Field label="供应商 ID" value={lot.supplierId} />
                <Field label="生产日期" value={lot.produceDate} />
                <Field label="过期日期" value={lot.expireDate} />
                <Field label="仓位" value={<span className="text-gray-400">未采集</span>} />
                <Field label="总量 (kg)" value={lot.initialQty} />
                <Field label="剩余量 (kg)" value={lot.remainingQty} />
                <Field
                  label="已用量 (kg)"
                  value={
                    <span className="text-orange-600">{usedQty}</span>
                  }
                />
                <Field label="创建时间" value={formatDateTime(lot.createdAt)} />
                <Field label="更新时间" value={formatDateTime(lot.updatedAt)} />
              </div>
            </CardContent>
          </Card>
        </div>

        {/* 右侧：出入库记录 40% */}
        <div className="lg:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle>出入库记录</CardTitle>
              <CardDescription>
                共 {transfers.length} 条记录
              </CardDescription>
            </CardHeader>
            <CardContent>
              {transfersQuery.isLoading ? (
                <div className="text-center py-8 text-sm text-gray-400">
                  加载中...
                </div>
              ) : transfersQuery.isError ? (
                <div className="text-center py-8 text-sm text-red-500">
                  加载失败
                </div>
              ) : transfers.length === 0 ? (
                <div className="text-center py-8 text-sm text-gray-400">
                  暂无出入库记录
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow className="bg-gray-50">
                      <TableHead className="px-3">类型</TableHead>
                      <TableHead className="px-3 text-right">数量</TableHead>
                      <TableHead className="px-3">仓位</TableHead>
                      <TableHead className="px-3">时间</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {transfers.map((t: TransferRecord) => (
                      <TableRow key={t.id}>
                        <TableCell className="px-3">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                              TRANSFER_TYPE_COLOR[t.type] ?? 'bg-gray-100 text-gray-600'
                            }`}
                          >
                            {TRANSFER_TYPE_LABEL[t.type] ?? t.type}
                          </span>
                        </TableCell>
                        <TableCell className="px-3 text-right text-sm text-gray-700">
                          {t.qty ?? '-'}
                        </TableCell>
                        <TableCell className="px-3 text-xs text-gray-600">
                          {t.toLocation ?? '-'}
                        </TableCell>
                        <TableCell className="px-3 text-xs text-gray-500">
                          {formatDateTime(t.createdAt)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      <BackButton onClick={() => router.push('/lots')} />

      {/* 转库 Dialog */}
      <TransferDialog
        open={showTransfer}
        onOpenChange={setShowTransfer}
        lotNo={lot.lotNo}
        remainingQty={lot.remainingQty ?? 0}
      />
    </div>
  );
}

function BackButton({ onClick }: { onClick: () => void }) {
  return (
    <div className="mt-6">
      <Button variant="outline" onClick={onClick}>
        ← 返回批次列表
      </Button>
    </div>
  );
}

export default function LotDetailPage() {
  return (
    <AuthGuard>
      <LotDetailPageInner />
    </AuthGuard>
  );
}

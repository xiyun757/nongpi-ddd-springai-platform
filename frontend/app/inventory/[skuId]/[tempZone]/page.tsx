'use client';

import { useState, useEffect, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
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
  fetchInventoryDetail,
  fetchInventoryLots,
  TEMP_ZONE_LABEL,
  STATUS_LABEL,
  STATUS_COLOR,
  LotItem,
} from '@/lib/api';
import { formatDateTime } from '@/lib/utils';
import OutboundDialog from '@/components/OutboundDialog';
import { SkeletonDetailPage } from '@/components/Skeleton';

function InventoryDetailPageInner() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();

  const skuId = Number(params?.skuId);
  const tempZone = String(params?.tempZone ?? '');
  const zoneLabel = TEMP_ZONE_LABEL[tempZone] ?? tempZone;

  const detailQuery = useQuery({
    queryKey: ['inventory-detail', skuId, tempZone],
    queryFn: () => fetchInventoryDetail(skuId, tempZone),
    enabled: !!skuId && !!tempZone,
    retry: false,
  });

  const lotsQuery = useQuery({
    queryKey: ['inventory-lots', skuId, tempZone],
    queryFn: () => fetchInventoryLots(skuId, tempZone),
    enabled: !!skuId && !!tempZone,
    retry: false,
  });

  const [activeLot, setActiveLot] = useState<LotItem | null>(null);
  const prevLotNo = useRef<string | null>(null);

  // 当 activeLot 从非空变为空（Dialog 关闭）时刷新本页数据
  // 出库成功后 OutboundDialog 会 invalidate ['lots']，但本页用的是独立 queryKey
  useEffect(() => {
    const currentLotNo = activeLot?.lotNo ?? null;
    // 仅在 Dialog 从打开→关闭的转换时刷新
    if (prevLotNo.current !== null && currentLotNo === null) {
      queryClient.invalidateQueries({ queryKey: ['inventory-detail', skuId, tempZone] });
      queryClient.invalidateQueries({ queryKey: ['inventory-lots', skuId, tempZone] });
      queryClient.invalidateQueries({ queryKey: ['inventory'] });
    }
    prevLotNo.current = currentLotNo;
  }, [activeLot, queryClient, skuId, tempZone]);

  // 加载中：骨架屏
  if (detailQuery.isLoading) {
    return (
      <div>
        <SkeletonDetailPage />
        <BackButton onClick={() => router.push('/inventory')} />
      </div>
    );
  }

  // 加载失败 / 库存不存在
  if (detailQuery.isError || !detailQuery.data) {
    const msg = detailQuery.error instanceof Error ? detailQuery.error.message : '';
    const isNotFound = msg.includes('不存在') || msg.includes('404');
    return (
      <div>
        <div className="bg-white rounded-lg shadow-sm ring-1 ring-gray-200 p-8 text-center">
          <p className="text-base font-medium text-gray-700">
            {isNotFound ? '库存记录不存在' : '加载失败，请重试'}
          </p>
          {msg && !isNotFound && (
            <p className="text-xs text-red-500 mt-2">{msg}</p>
          )}
        </div>
        <BackButton onClick={() => router.push('/inventory')} />
      </div>
    );
  }

  const inv = detailQuery.data;
  const lots = lotsQuery.data ?? [];
  const availableColor = inv.availableQty <= 0 ? 'text-red-600' : 'text-green-600';

  return (
    <div>
      {/* 顶部标题 */}
      <div className="mb-4">
        <h1 className="text-xl font-bold text-gray-900">
          库存详情 - SKU #{inv.skuId}（{zoneLabel}）
        </h1>
        <p className="text-sm text-gray-500 mt-1">
          查看该 SKU+温区下的库存概况与关联批次
        </p>
      </div>

      {/* 顶部概览卡片 */}
      <Card>
        <CardHeader>
          <CardTitle>库存概况</CardTitle>
          <CardDescription>
            版本号 v{inv.version} · 最后更新 {formatDateTime(inv.updatedAt)}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-3 gap-4">
            <Metric label="总库存量 (kg)" value={inv.totalQty} color="text-gray-900" />
            <Metric label="冻结量 (kg)" value={inv.frozenQty} color="text-amber-600" />
            <Metric label="可用量 (kg)" value={inv.availableQty} color={availableColor} />
          </div>
        </CardContent>
      </Card>

      {/* 底部批次列表卡片 */}
      <div className="mt-4">
        <Card>
          <CardHeader>
            <CardTitle>关联批次</CardTitle>
            <CardDescription>共 {lots.length} 条批次</CardDescription>
          </CardHeader>
          <CardContent>
            {lotsQuery.isLoading ? (
              <div className="text-center py-8 text-sm text-gray-400">加载中...</div>
            ) : lotsQuery.isError ? (
              <div className="text-center py-8 text-sm text-red-500">加载失败</div>
            ) : lots.length === 0 ? (
              <div className="text-center py-8 text-sm text-gray-400">
                该 SKU+温区下暂无批次记录
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow className="bg-gray-50">
                    <TableHead className="px-4">批次号</TableHead>
                    <TableHead className="px-4">状态</TableHead>
                    <TableHead className="px-4 text-right">剩余量 (kg)</TableHead>
                    <TableHead className="px-4">生产日期</TableHead>
                    <TableHead className="px-4">过期日期</TableHead>
                    <TableHead className="px-4 text-center">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {lots.map((lot) => (
                    <TableRow key={lot.lotNo}>
                      <TableCell className="px-4 font-mono text-xs">
                        <Link
                          href={`/lots/${lot.lotNo}`}
                          className="text-blue-600 hover:underline cursor-pointer"
                        >
                          {lot.lotNo}
                        </Link>
                      </TableCell>
                      <TableCell className="px-4">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                            STATUS_COLOR[lot.status] ?? 'bg-gray-100 text-gray-600'
                          }`}
                        >
                          {STATUS_LABEL[lot.status] ?? lot.status}
                        </span>
                      </TableCell>
                      <TableCell className="px-4 text-right text-sm text-gray-700">
                        {lot.remainingQty}
                      </TableCell>
                      <TableCell className="px-4 text-xs text-gray-600">
                        {lot.produceDate}
                      </TableCell>
                      <TableCell className="px-4 text-xs text-gray-600">
                        {lot.expireDate}
                      </TableCell>
                      <TableCell className="px-4 text-center">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setActiveLot(lot)}
                          disabled={lot.remainingQty <= 0}
                        >
                          出库
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </div>

      <BackButton onClick={() => router.push('/inventory')} />

      {/* 出库 Dialog */}
      {activeLot && (
        <OutboundDialog
          open
          onOpenChange={(o) => {
            if (!o) setActiveLot(null);
          }}
          lot={activeLot}
        />
      )}
    </div>
  );
}

function Metric({
  label,
  value,
  color,
}: {
  label: string;
  value: React.ReactNode;
  color: string;
}) {
  return (
    <div className="bg-gray-50 rounded-lg p-4">
      <div className="text-xs text-gray-500">{label}</div>
      <div className={`text-3xl font-bold mt-1 ${color}`}>{value}</div>
    </div>
  );
}

function BackButton({ onClick }: { onClick: () => void }) {
  return (
    <div className="mt-6">
      <Button variant="outline" onClick={onClick}>
        ← 返回库存列表
      </Button>
    </div>
  );
}

export default function InventoryDetailPage() {
  return (
    <AuthGuard>
      <InventoryDetailPageInner />
    </AuthGuard>
  );
}

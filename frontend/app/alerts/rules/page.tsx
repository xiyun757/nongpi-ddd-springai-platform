'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  fetchAlertRules,
  toggleAlertRule,
  deleteAlertRule,
  ALERT_LEVEL_LABEL,
  ALERT_LEVEL_COLOR,
  TEMP_ZONE_LABEL,
  AlertRule,
} from '@/lib/api';
import { showActionSuccess, showActionError } from '@/lib/toast';
import CreateRuleDialog from '@/components/CreateRuleDialog';
import { SkeletonTable } from '@/components/Skeleton';
import { formatDateTime } from '@/lib/utils';

function SwitchToggle({
  checked,
  disabled,
  onChange,
}: {
  checked: boolean;
  disabled?: boolean;
  onChange: () => void;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={onChange}
      className={`relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors disabled:opacity-50 ${
        checked ? 'bg-blue-600' : 'bg-gray-300'
      }`}
    >
      <span
        className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
          checked ? 'translate-x-4' : 'translate-x-0.5'
        }`}
      />
    </button>
  );
}

function AlertRulesPageInner() {
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<AlertRule | null>(null);

  // 筛选状态
  const [searchInput, setSearchInput] = useState('');
  const [skuId, setSkuId] = useState('');
  const [tempZone, setTempZone] = useState('');
  const [enabled, setEnabled] = useState('');

  const skuIdNum = skuId ? Number(skuId) : undefined;
  const enabledBool = enabled === '' ? undefined : enabled === 'true';

  const { data, isLoading, error } = useQuery({
    queryKey: ['alert-rules', skuId, tempZone, enabled],
    queryFn: () => fetchAlertRules({
      skuId: skuIdNum,
      tempZone: tempZone || undefined,
      enabled: enabledBool,
    }),
  });

  const rules = data ?? [];

  const handleSearch = () => {
    setSkuId(searchInput.trim());
  };

  const handleReset = () => {
    setSearchInput('');
    setSkuId('');
    setTempZone('');
    setEnabled('');
  };

  const toggleMut = useMutation({
    mutationFn: (rule: AlertRule) => toggleAlertRule(rule.id, !rule.enabled),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alert-rules'] });
      showActionSuccess('更新规则状态');
    },
    onError: (err: Error) => {
      showActionError('更新规则状态', err.message);
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => deleteAlertRule(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alert-rules'] });
      setPendingDelete(null);
      showActionSuccess('删除规则');
    },
    onError: (err: Error) => {
      showActionError('删除规则', err.message);
    },
  });

  return (
    <div className="animate-fadeIn">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h1 className="text-xl font-bold text-gray-900">预警规则管理</h1>
          <p className="text-sm text-gray-500 mt-1">配置批次临期预警的触发条件</p>
        </div>
        <Button variant="default" onClick={() => setShowCreate(true)}>
          新建规则
        </Button>
      </div>

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
          <div className="grid grid-cols-2 gap-3 max-w-md">
            <div>
              <label className="block text-xs text-gray-500 mb-1">温区</label>
              <select
                value={tempZone}
                onChange={(e) => setTempZone(e.target.value)}
                className="border rounded px-2 py-1.5 w-full text-sm bg-white"
              >
                <option value="">全部</option>
                <option value="FREEZE">冷冻</option>
                <option value="FRESH">冷藏</option>
                <option value="NORMAL">常温</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">启用状态</label>
              <select
                value={enabled}
                onChange={(e) => setEnabled(e.target.value)}
                className="border rounded px-2 py-1.5 w-full text-sm bg-white"
              >
                <option value="">全部</option>
                <option value="true">已启用</option>
                <option value="false">已禁用</option>
              </select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 表格 */}
      <div className="bg-white rounded-lg shadow-sm ring-1 ring-gray-200 overflow-hidden">
        {isLoading ? (
          <SkeletonTable columns={8} rows={5} />
        ) : (
        <Table>
          <TableHeader>
            <TableRow className="bg-gray-50">
              <TableHead className="px-4">ID</TableHead>
              <TableHead className="px-4">适用 SKU</TableHead>
              <TableHead className="px-4">适用温区</TableHead>
              <TableHead className="px-4">过期阈值</TableHead>
              <TableHead className="px-4">预警级别</TableHead>
              <TableHead className="px-4">状态</TableHead>
              <TableHead className="px-4">创建时间</TableHead>
              <TableHead className="px-4 text-center">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {error && (
              <TableRow>
                <TableCell colSpan={8} className="text-center py-8 text-red-500">
                  加载失败，请重试
                </TableCell>
              </TableRow>
            )}

            {!error && rules.length === 0 && (
              <TableRow>
                <TableCell colSpan={8} className="text-center py-8 text-gray-400">
                  暂无预警规则，点击右上角新建
                </TableCell>
              </TableRow>
            )}

            {!error && rules.map((r) => (
              <TableRow key={r.id} className="hover:bg-gray-50 transition-colors duration-150">
                <TableCell className="px-4 text-sm text-gray-600">{r.id}</TableCell>
                <TableCell className="px-4 text-sm">
                  {r.skuId === null || r.skuId === undefined ? (
                    <span className="text-xs text-gray-500">全部</span>
                  ) : (
                    r.skuId
                  )}
                </TableCell>
                <TableCell className="px-4 text-sm">
                  <span className="text-xs text-gray-600">
                    {r.tempZone === null || r.tempZone === undefined
                      ? '全部'
                      : TEMP_ZONE_LABEL[r.tempZone] ?? r.tempZone}
                  </span>
                </TableCell>
                <TableCell className="px-4 text-sm text-gray-700">
                  {r.thresholdDays} 天
                </TableCell>
                <TableCell className="px-4">
                  <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                    ALERT_LEVEL_COLOR[r.alertLevel] ?? 'text-gray-600 bg-gray-50'
                  }`}>
                    {ALERT_LEVEL_LABEL[r.alertLevel] ?? r.alertLevel}
                  </span>
                </TableCell>
                <TableCell className="px-4">
                  <SwitchToggle
                    checked={r.enabled}
                    disabled={toggleMut.isPending}
                    onChange={() => toggleMut.mutate(r)}
                  />
                </TableCell>
                <TableCell className="px-4 text-xs text-gray-500">
                  {formatDateTime(r.createdAt)}
                </TableCell>
                <TableCell className="px-4 text-center">
                  <Button
                    size="sm"
                    variant="destructive"
                    onClick={() => setPendingDelete(r)}
                  >
                    删除
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        )}
      </div>

      {/* 规则总数 */}
      <div className="mt-3 text-xs text-gray-500">
        共 {rules.length} 条规则
      </div>

      {/* 新建 Dialog */}
      <CreateRuleDialog
        open={showCreate}
        onOpenChange={setShowCreate}
      />

      {/* 删除确认 Dialog */}
      {pendingDelete && (
        <DeleteConfirmDialog
          rule={pendingDelete}
          loading={deleteMut.isPending}
          onCancel={() => {
            if (!deleteMut.isPending) setPendingDelete(null);
          }}
          onConfirm={() => deleteMut.mutate(pendingDelete.id)}
        />
      )}
    </div>
  );
}

// ── 删除确认 Dialog ──────────────────────────────────────

function DeleteConfirmDialog({
  rule,
  loading,
  onCancel,
  onConfirm,
}: {
  rule: AlertRule;
  loading: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const skuLabel = rule.skuId === null || rule.skuId === undefined ? '全部' : String(rule.skuId);
  const zoneLabel = rule.tempZone === null || rule.tempZone === undefined
    ? '全部'
    : TEMP_ZONE_LABEL[rule.tempZone] ?? rule.tempZone;

  return (
    <Dialog open onOpenChange={(o) => { if (!o) onCancel(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>确认删除规则</DialogTitle>
          <DialogDescription>
            删除后不可恢复，确定要删除此预警规则吗？
          </DialogDescription>
        </DialogHeader>

        <div className="text-sm text-gray-700 bg-gray-50 rounded-lg p-3 space-y-1">
          <div>规则 ID：<span className="font-medium">{rule.id}</span></div>
          <div>适用 SKU：<span className="font-medium">{skuLabel}</span></div>
          <div>适用温区：<span className="font-medium">{zoneLabel}</span></div>
          <div>过期阈值：<span className="font-medium">{rule.thresholdDays} 天</span></div>
          <div>预警级别：
            <span className="font-medium">
              {ALERT_LEVEL_LABEL[rule.alertLevel] ?? rule.alertLevel}
            </span>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onCancel} disabled={loading}>
            取消
          </Button>
          <Button variant="destructive" onClick={onConfirm} disabled={loading}>
            {loading ? '删除中...' : '确认删除'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function AlertRulesPage() {
  return (
    <AuthGuard>
      <AlertRulesPageInner />
    </AuthGuard>
  );
}

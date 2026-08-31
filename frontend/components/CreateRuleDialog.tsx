'use client';

import { useState, FormEvent } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { createAlertRule } from '@/lib/api';
import { showError, showActionSuccess, showActionError } from '@/lib/toast';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const TEMP_ZONE_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'FREEZE', label: '冷冻' },
  { value: 'FRESH', label: '冷藏' },
  { value: 'NORMAL', label: '常温' },
];

const ALERT_LEVEL_OPTIONS: { value: string; label: string }[] = [
  { value: 'CRITICAL', label: '严重' },
  { value: 'WARNING', label: '警告' },
  { value: 'INFO', label: '提示' },
];

const INPUT_CLASS =
  'w-full h-9 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring';

export default function CreateRuleDialog({ open, onOpenChange }: Props) {
  const queryClient = useQueryClient();
  const [skuId, setSkuId] = useState('');
  const [tempZone, setTempZone] = useState('');
  const [thresholdDays, setThresholdDays] = useState('');
  const [alertLevel, setAlertLevel] = useState('WARNING');
  const [submitting, setSubmitting] = useState(false);

  const reset = () => {
    setSkuId('');
    setTempZone('');
    setThresholdDays('');
    setAlertLevel('WARNING');
    setSubmitting(false);
  };

  const handleOpenChange = (next: boolean) => {
    if (!next) reset();
    onOpenChange(next);
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    const days = Number(thresholdDays);
    if (!thresholdDays || isNaN(days) || !Number.isInteger(days) || days <= 0) {
      showError('过期阈值必须为正整数');
      return;
    }

    const skuIdNum = skuId.trim() === '' ? null : Number(skuId);
    if (skuIdNum !== null && (isNaN(skuIdNum) || skuIdNum <= 0)) {
      showError('SKU ID 必须为正整数');
      return;
    }

    setSubmitting(true);
    try {
      await createAlertRule({
        skuId: skuIdNum,
        tempZone: tempZone || null,
        thresholdDays: days,
        alertLevel,
      });
      await queryClient.invalidateQueries({ queryKey: ['alert-rules'] });
      showActionSuccess('创建规则');
      handleOpenChange(false);
    } catch (err) {
      showActionError('创建规则', err instanceof Error ? err.message : undefined);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新建预警规则</DialogTitle>
          <DialogDescription>
            配置批次过期前的预警触发条件
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs text-muted-foreground mb-1">
              SKU ID（可选，留空表示全局规则）
            </label>
            <input
              type="number"
              min="1"
              step="1"
              value={skuId}
              onChange={(e) => setSkuId(e.target.value)}
              placeholder="留空表示全局规则"
              className={INPUT_CLASS}
            />
          </div>

          <div>
            <label className="block text-xs text-muted-foreground mb-1">
              适用温区
            </label>
            <select
              value={tempZone}
              onChange={(e) => setTempZone(e.target.value)}
              className={INPUT_CLASS}
            >
              {TEMP_ZONE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs text-muted-foreground mb-1">
              过期阈值（天） <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              min="1"
              step="1"
              value={thresholdDays}
              onChange={(e) => setThresholdDays(e.target.value)}
              placeholder="如 3 表示过期前 3 天触发"
              required
              className={INPUT_CLASS}
            />
          </div>

          <div>
            <label className="block text-xs text-muted-foreground mb-1">
              预警级别 <span className="text-red-500">*</span>
            </label>
            <select
              value={alertLevel}
              onChange={(e) => setAlertLevel(e.target.value)}
              className={INPUT_CLASS}
            >
              {ALERT_LEVEL_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => handleOpenChange(false)}
              disabled={submitting}
            >
              取消
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? '创建中...' : '确认创建'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

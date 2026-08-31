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
import { submitOutbound, LotItem } from '@/lib/api';
import { showError, showActionSuccess, showActionError } from '@/lib/toast';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  lot: LotItem;
}

export default function OutboundDialog({ open, onOpenChange, lot }: Props) {
  const queryClient = useQueryClient();
  const [qty, setQty] = useState('');
  const [toLocation, setToLocation] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const reset = () => {
    setQty('');
    setToLocation('');
    setSubmitting(false);
  };

  const handleOpenChange = (next: boolean) => {
    if (!next) reset();
    onOpenChange(next);
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    const qtyNum = Number(qty);
    if (!qty || isNaN(qtyNum) || qtyNum <= 0 || !Number.isInteger(qtyNum)) {
      showError('出库数量必须大于 0');
      return;
    }
    if (qtyNum > lot.remainingQty) {
      showError(`出库数量不能超过剩余量 ${lot.remainingQty} kg`);
      return;
    }
    if (!toLocation.trim()) {
      showError('请输入目标仓位');
      return;
    }

    setSubmitting(true);
    try {
      const result = await submitOutbound({
        skuId: lot.skuId,
        tempZone: lot.tempZone,
        qty: qtyNum,
        toLocation: toLocation.trim(),
      });
      await queryClient.invalidateQueries({ queryKey: ['lots'] });
      showActionSuccess('出库', result.lotNo);
      handleOpenChange(false);
    } catch (err) {
      showActionError('出库', err instanceof Error ? err.message : undefined);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>出库 - 批次 {lot.lotNo}</DialogTitle>
          <DialogDescription>
            可出库：{lot.remainingQty} kg（温区：{lot.tempZone}）
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs text-muted-foreground mb-1">
              出库数量 (kg)
            </label>
            <input
              type="number"
              min="1"
              step="1"
              max={lot.remainingQty}
              value={qty}
              onChange={(e) => setQty(e.target.value)}
              placeholder="正整数"
              required
              className="w-full h-9 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>

          <div>
            <label className="block text-xs text-muted-foreground mb-1">目标仓位</label>
            <input
              type="text"
              value={toLocation}
              onChange={(e) => setToLocation(e.target.value)}
              placeholder="如 A-01、B-03"
              required
              className="w-full h-9 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
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
              {submitting ? '提交中...' : '确认出库'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

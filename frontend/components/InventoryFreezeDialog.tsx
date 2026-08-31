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
import { fetchJsonWithAuth } from '@/lib/api';
import { showError, showActionSuccess, showActionError } from '@/lib/toast';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  skuId: number;
  tempZone: string;
  currentFrozenQty: number;
  mode: 'freeze' | 'unfreeze';
}

export default function InventoryFreezeDialog({
  open,
  onOpenChange,
  skuId,
  tempZone,
  currentFrozenQty,
  mode,
}: Props) {
  const queryClient = useQueryClient();
  const [qty, setQty] = useState('');
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const isUnfreeze = mode === 'unfreeze';
  const title = isUnfreeze ? '解冻库存' : '冻结库存';
  const desc = `SKU ${skuId} / ${tempZone}，当前冻结量 ${currentFrozenQty} kg`;
  const actionLabel = isUnfreeze ? '解冻' : '冻结';

  const reset = () => {
    setQty('');
    setRemark('');
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
      showError('请输入正整数');
      return;
    }

    if (isUnfreeze && qtyNum > currentFrozenQty) {
      showError(`解冻数量不能超过当前冻结量 ${currentFrozenQty} kg`);
      return;
    }

    setSubmitting(true);
    try {
      const url = isUnfreeze ? '/inventory/unfreeze' : '/inventory/freeze';
      await fetchJsonWithAuth(url, {
        method: 'POST',
        body: JSON.stringify({ skuId, tempZone, qty: qtyNum }),
      });
      await queryClient.invalidateQueries({ queryKey: ['inventory'] });
      showActionSuccess(actionLabel, `${qtyNum} kg`);
      handleOpenChange(false);
    } catch (err) {
      showActionError(actionLabel, err instanceof Error ? err.message : undefined);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{desc}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs text-muted-foreground mb-1">数量 (kg)</label>
            <input
              type="number"
              min="1"
              step="1"
              max={isUnfreeze ? currentFrozenQty : undefined}
              value={qty}
              onChange={(e) => setQty(e.target.value)}
              placeholder="正整数"
              required
              className="w-full h-9 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>

          <div>
            <label className="block text-xs text-muted-foreground mb-1">备注（可选）</label>
            <input
              type="text"
              value={remark}
              onChange={(e) => setRemark(e.target.value)}
              placeholder="说明操作原因"
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
              {submitting ? '提交中...' : '确认'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

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
import { fetchJsonWithAuth, TEMP_ZONE_LABEL } from '@/lib/api';
import { showError, showActionSuccess, showActionError } from '@/lib/toast';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  skuId: number;
  tempZone: string;
  currentQty: number;
}

export default function InventoryAdjustDialog({
  open,
  onOpenChange,
  skuId,
  tempZone,
  currentQty,
}: Props) {
  const queryClient = useQueryClient();
  const [type, setType] = useState<'increase' | 'decrease'>('increase');
  const [qty, setQty] = useState('');
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const reset = () => {
    setType('increase');
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

    setSubmitting(true);
    try {
      const delta = type === 'increase' ? qtyNum : -qtyNum;
      await fetchJsonWithAuth('/inventory/adjust', {
        method: 'POST',
        body: JSON.stringify({ skuId, tempZone, delta }),
      });
      await queryClient.invalidateQueries({ queryKey: ['inventory'] });
      showActionSuccess('库存调整', `${skuId} ${TEMP_ZONE_LABEL[tempZone] ?? tempZone}`);
      handleOpenChange(false);
    } catch (err) {
      showActionError('库存调整', err instanceof Error ? err.message : undefined);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>调整库存</DialogTitle>
          <DialogDescription>
            SKU {skuId} / {tempZone}，当前总量 {currentQty} kg
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs text-muted-foreground mb-1">操作类型</label>
            <select
              value={type}
              onChange={(e) => setType(e.target.value as 'increase' | 'decrease')}
              className="w-full h-9 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <option value="increase">增加库存</option>
              <option value="decrease">减少库存</option>
            </select>
          </div>

          <div>
            <label className="block text-xs text-muted-foreground mb-1">数量 (kg)</label>
            <input
              type="number"
              min="1"
              step="1"
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
              placeholder="说明调整原因"
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

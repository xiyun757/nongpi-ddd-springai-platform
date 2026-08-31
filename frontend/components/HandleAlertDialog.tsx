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
import { handleAlert } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import { showError, showActionSuccess, showActionError } from '@/lib/toast';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  alertId: number;
  lotNo: string;
  message: string;
}

export default function HandleAlertDialog({
  open,
  onOpenChange,
  alertId,
  lotNo,
  message,
}: Props) {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  // 父组件条件渲染本 Dialog（开则挂载/关则卸载），每次打开都是新实例，
  // 故默认值用 useState 惰性初始化即可，无需 effect 重置
  const [handler, setHandler] = useState(() => user?.username ?? '');
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    if (!handler.trim()) {
      showError('请输入处理人');
      return;
    }

    setSubmitting(true);
    try {
      await handleAlert(alertId, handler.trim());
      await queryClient.invalidateQueries({ queryKey: ['alerts'] });
      showActionSuccess('处理预警', `批次 ${lotNo}`);
      onOpenChange(false);
    } catch (err) {
      showActionError('处理预警', err instanceof Error ? err.message : undefined);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>处理预警 - 批次 {lotNo}</DialogTitle>
          <DialogDescription>{message}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-xs text-muted-foreground mb-1">
              处理人 <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={handler}
              onChange={(e) => setHandler(e.target.value)}
              placeholder="处理人姓名"
              required
              className="w-full h-9 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>

          <div>
            <label className="block text-xs text-muted-foreground mb-1">
              处理说明（可选）
            </label>
            <textarea
              value={remark}
              onChange={(e) => setRemark(e.target.value)}
              placeholder="说明处理方式"
              rows={3}
              className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring resize-none"
            />
            <p className="text-xs text-gray-400 mt-1">* 当前后端暂未支持，仅前端展示</p>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={submitting}
            >
              取消
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? '处理中...' : '确认处理'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

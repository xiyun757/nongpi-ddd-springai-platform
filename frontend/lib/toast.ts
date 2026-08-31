import { toast } from 'sonner';

/**
 * 显示成功 Toast
 */
export function showSuccess(message: string) {
  toast.success(message, { duration: 3000 });
}

/**
 * 显示错误 Toast
 */
export function showError(message: string) {
  toast.error(message, { duration: 4000 });
}

/**
 * 显示加载中 Toast（返回 dismiss 函数）
 */
export function showLoading(message: string): () => void {
  const id = toast.loading(message);
  return () => toast.dismiss(id);
}

/**
 * 显示操作成功，并附带详情
 */
export function showActionSuccess(action: string, detail?: string) {
  toast.success(`${action}成功${detail ? `：${detail}` : ''}`, { duration: 3000 });
}

/**
 * 显示操作失败，并附带错误信息
 */
export function showActionError(action: string, error?: string) {
  toast.error(`${action}失败${error ? `：${error}` : ''}`, { duration: 5000 });
}

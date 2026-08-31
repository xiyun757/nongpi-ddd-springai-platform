interface EmptyStateProps {
  icon?: string;
  message?: string;
  description?: string;
}

/**
 * 统一空状态：图标 + 主文案 + 辅助描述
 * 替换各表格"暂无数据"纯文字，提升一致性
 */
export function EmptyState({
  icon = '📭',
  message = '暂无数据',
  description,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      <div className="text-4xl mb-3 opacity-60">{icon}</div>
      <p className="text-sm font-medium text-gray-500">{message}</p>
      {description && (
        <p className="text-xs text-gray-400 mt-1">{description}</p>
      )}
    </div>
  );
}

type BadgeVariant =
  | 'gray'
  | 'blue'
  | 'green'
  | 'amber'
  | 'red'
  | 'orange';

interface StatusBadgeProps {
  variant: BadgeVariant;
  children: React.ReactNode;
}

const VARIANT_STYLES: Record<BadgeVariant, string> = {
  gray: 'bg-gray-100 text-gray-600',
  blue: 'bg-blue-50 text-blue-700',
  green: 'bg-green-50 text-green-700',
  amber: 'bg-amber-50 text-amber-700',
  red: 'bg-red-50 text-red-700',
  orange: 'bg-orange-50 text-orange-700',
};

/**
 * 统一状态徽章：温区 / 批次状态 / 预警级别 / 处理状态
 * 替换各页手写 span className，复用全局配色体系
 */
export function StatusBadge({ variant, children }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${VARIANT_STYLES[variant]}`}
    >
      {children}
    </span>
  );
}

/** 批次状态映射到 Badge variant */
export const LOT_STATUS_BADGE: Record<
  string,
  { variant: BadgeVariant; label: string }
> = {
  IN_STOCK: { variant: 'green', label: '在库' },
  PARTIAL_OUT: { variant: 'blue', label: '部分出库' },
  FULLY_OUT: { variant: 'gray', label: '已出清' },
  FROZEN: { variant: 'amber', label: '已冻结' },
  TERMINATED: { variant: 'red', label: '已终止' },
  EXPIRED: { variant: 'red', label: '已过期' },
};

/** 温区映射到 Badge variant */
export const TEMP_ZONE_BADGE: Record<
  string,
  { variant: BadgeVariant; label: string }
> = {
  FREEZE: { variant: 'blue', label: '冷冻' },
  FRESH: { variant: 'green', label: '冷藏' },
  NORMAL: { variant: 'amber', label: '常温' },
};

/** 预警级别映射到 Badge variant */
export const ALERT_LEVEL_BADGE: Record<
  string,
  { variant: BadgeVariant; label: string }
> = {
  CRITICAL: { variant: 'red', label: '严重' },
  WARNING: { variant: 'amber', label: '警告' },
  INFO: { variant: 'blue', label: '提示' },
};

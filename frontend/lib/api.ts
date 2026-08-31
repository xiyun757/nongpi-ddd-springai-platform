const BASE_URL = '/api';
const TOKEN_KEY = 'auth_token';

/**
 * 带认证的 fetch 封装。
 * 自动从 localStorage 读取 token 并注入 Authorization 头。
 * 401 时清除 token 并跳转登录页。
 */
export async function fetchWithAuth(
  url: string,
  options: RequestInit = {}
): Promise<Response> {
  let token: string | null = null;
  if (typeof window !== 'undefined') {
    token = localStorage.getItem(TOKEN_KEY);
  }

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(`${BASE_URL}${url}`, { ...options, headers });

  // 401: token 失效或未登录；403 且 token 存在时通常也是认证问题（如 token 过期后端误报 403）
  // 统一清除 token 并跳转登录页
  if ((res.status === 401 || (res.status === 403 && token)) && typeof window !== 'undefined') {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem('auth_user');
    // 避免在 /login 页面循环跳转
    if (!window.location.pathname.startsWith('/login')) {
      window.location.href = '/login';
    }
  }

  return res;
}

/**
 * 带认证 + JSON 解析的便捷方法，自动抛出业务异常。
 * @throws Error(message) — message 为后端返回的 error 字段
 */
export async function fetchJsonWithAuth<T>(
  url: string,
  options: RequestInit = {}
): Promise<T> {
  const res = await fetchWithAuth(url, options);
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: `HTTP ${res.status}` }));
    throw new Error(err.error || `请求失败: HTTP ${res.status}`);
  }
  return res.json() as Promise<T>;
}

// ── 批次 ────────────────────────────────────────────────

export interface LotItem {
  lotNo: string;
  skuId: number;
  tempZone: string;
  produceDate: string;
  expireDate: string;
  initialQty: number;
  remainingQty: number;
  status: string;
  supplierId: number;
}

export interface LotPage {
  records: LotItem[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** 批次状态展示文案（多个页面共用，避免重复定义） */
export const STATUS_LABEL: Record<string, string> = {
  IN_STOCK: '在库',
  PARTIAL_OUT: '部分出库',
  FULLY_OUT: '已出清',
  EXPIRED: '已过期',
};

/** 批次状态徽章样式（多个页面共用，避免重复定义） */
export const STATUS_COLOR: Record<string, string> = {
  IN_STOCK: 'bg-green-100 text-green-700',
  PARTIAL_OUT: 'bg-yellow-100 text-yellow-700',
  FULLY_OUT: 'bg-gray-100 text-gray-600',
  EXPIRED: 'bg-red-100 text-red-700',
};

/** 批次列表 */
export const fetchLots = async (params: {
  page: number;
  size: number;
  skuId?: number;
  tempZone?: string;
  status?: string;
  lotNo?: string;
}) => {
  const search = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
  });
  if (params.skuId) search.set('skuId', String(params.skuId));
  if (params.tempZone) search.set('tempZone', params.tempZone);
  if (params.status) search.set('status', params.status);
  if (params.lotNo) search.set('lotNo', params.lotNo);

  return fetchJsonWithAuth<LotPage>(`/lots/list?${search}`);
};

/** 批次详情 */
export const fetchLot = async (lotNo: string) => {
  return fetchJsonWithAuth<LotItem>(`/lots/${lotNo}`);
};

/** 入库 */
export const inbound = async (data: {
  skuId: number;
  tempZone: string;
  produceDate: string;
  expireDate: string;
  qty: number;
  supplierId: number;
}) => {
  return fetchJsonWithAuth<LotItem>(`/lots/inbound`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
};

/** 出库 */
export const outbound = async (data: {
  skuId: number;
  tempZone: string;
  qty: number;
  toLocation: string;
}) => {
  return fetchJsonWithAuth(`/lots/outbound`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
};

// ── 库存 ────────────────────────────────────────────────

export interface InventoryItem {
  skuId: number;
  tempZone: string;
  totalQty: number;
  frozenQty: number;
  availableQty: number;
  updatedAt?: string;
}

export interface InventoryPage {
  records: InventoryItem[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** 温区枚举到中文映射 */
export const TEMP_ZONE_LABEL: Record<string, string> = {
  FREEZE: '冷冻',
  FRESH: '冷藏',
  NORMAL: '常温',
};

/** 库存分页列表 */
export const fetchInventoryList = async (params: {
  page: number;
  size: number;
  skuId?: number;
  tempZone?: string;
  minQty?: number;
  maxQty?: number;
}) => {
  const search = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
  });
  if (params.skuId) search.set('skuId', String(params.skuId));
  if (params.tempZone) search.set('tempZone', params.tempZone);
  if (params.minQty !== undefined) search.set('minQty', String(params.minQty));
  if (params.maxQty !== undefined) search.set('maxQty', String(params.maxQty));

  return fetchJsonWithAuth<InventoryPage>(`/inventory/list?${search}`);
};

// ── 预警模块 ────────────────────────────────────────────

export interface AlertRecord {
  id: number;
  lotNo: string;
  alertLevel: string; // CRITICAL / WARNING / INFO
  message: string;
  handled: boolean;
  handler?: string;
  handledAt?: string;
  createdAt: string;
}

export interface AlertPage {
  records: AlertRecord[];
  total: number;
  current: number;
  size: number;
}

export const ALERT_LEVEL_LABEL: Record<string, string> = {
  CRITICAL: '严重',
  WARNING: '警告',
  INFO: '提示',
};

export const ALERT_LEVEL_COLOR: Record<string, string> = {
  CRITICAL: 'text-red-600 bg-red-50',
  WARNING: 'text-yellow-600 bg-yellow-50',
  INFO: 'text-blue-600 bg-blue-50',
};

export async function fetchAlerts(params: {
  handled?: boolean;
  alertLevel?: string;
  page?: number;
  size?: number;
  lotNo?: string;
  startDate?: string;
  endDate?: string;
}): Promise<AlertPage> {
  const searchParams = new URLSearchParams();
  if (params.handled !== undefined) searchParams.set('handled', String(params.handled));
  if (params.alertLevel) searchParams.set('alertLevel', params.alertLevel);
  if (params.page) searchParams.set('page', String(params.page));
  if (params.size) searchParams.set('size', String(params.size));
  if (params.lotNo) searchParams.set('lotNo', params.lotNo);
  if (params.startDate) searchParams.set('startDate', params.startDate);
  if (params.endDate) searchParams.set('endDate', params.endDate);
  return fetchJsonWithAuth<AlertPage>(`/alerts?${searchParams.toString()}`);
}

export async function handleAlert(id: number, handler: string): Promise<void> {
  await fetchJsonWithAuth(`/alerts/${id}/handle`, {
    method: 'POST',
    body: JSON.stringify({ handler }),
  });
}

// ── 预警规则模块 ────────────────────────────────────────────

export interface AlertRule {
  id: number;
  skuId: number | null; // null 表示全局规则
  tempZone: string | null; // null 表示所有温区
  thresholdDays: number; // 过期前多少天触发预警
  alertLevel: string; // CRITICAL / WARNING / INFO
  enabled: boolean;
  createdAt: string;
}

export interface CreateRuleRequest {
  skuId?: number | null;
  tempZone?: string | null;
  thresholdDays: number;
  alertLevel: string;
}

export async function fetchAlertRules(params?: {
  skuId?: number;
  tempZone?: string;
  enabled?: boolean;
}): Promise<AlertRule[]> {
  const searchParams = new URLSearchParams();
  if (params?.skuId) searchParams.set('skuId', String(params.skuId));
  if (params?.tempZone) searchParams.set('tempZone', params.tempZone);
  if (params?.enabled !== undefined) searchParams.set('enabled', String(params.enabled));
  const qs = searchParams.toString();
  const url = qs ? `/alerts/rules?${qs}` : `/alerts/rules`;
  const data = await fetchJsonWithAuth<AlertRule[] | { records: AlertRule[] }>(url);
  return Array.isArray(data) ? data : (data.records ?? []);
}

export async function createAlertRule(rule: CreateRuleRequest): Promise<AlertRule> {
  return fetchJsonWithAuth<AlertRule>(`/alerts/rules`, {
    method: 'POST',
    body: JSON.stringify(rule),
  });
}

export async function toggleAlertRule(id: number, enabled: boolean): Promise<AlertRule> {
  return fetchJsonWithAuth<AlertRule>(`/alerts/rules/${id}/toggle`, {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  });
}

export async function deleteAlertRule(id: number): Promise<void> {
  const res = await fetchWithAuth(`/alerts/rules/${id}`, {
    method: 'DELETE',
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: `HTTP ${res.status}` }));
    throw new Error(err.error || `删除失败: HTTP ${res.status}`);
  }
}

// ── 出库模块 ────────────────────────────────────────────

export interface OutboundRequest {
  skuId: number;
  tempZone: string;
  qty: number;
  toLocation: string;
}

export interface OutboundResult {
  lotNo: string;
  outQty: number;
  remainingQty: number;
  toLocation: string;
  success: boolean;
  message?: string;
}

export async function submitOutbound(data: OutboundRequest): Promise<OutboundResult> {
  return fetchJsonWithAuth<OutboundResult>('/lots/outbound', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

// ── 仪表盘模块 ──────────────────────────────────────────

export interface DashboardStats {
  totalLots: number;
  totalInventory: number;
  pendingAlerts: number;
}

/** 获取批次总数（通过分页接口取 total） */
export async function fetchTotalLots(): Promise<number> {
  const data = await fetchJsonWithAuth<LotPage>('/lots/list?page=1&size=1');
  return data.total ?? 0;
}

/** 获取总库存量（通过分页接口取 records，累加 totalQty） */
export async function fetchTotalInventoryQty(): Promise<number> {
  const data = await fetchJsonWithAuth<InventoryPage>('/inventory/list?page=1&size=100');
  if (!data.records || data.records.length === 0) return 0;
  return data.records.reduce((acc, r) => acc + (r.totalQty || 0), 0);
}

/** 获取未处理预警数 */
export async function fetchPendingAlerts(): Promise<number> {
  const data = await fetchJsonWithAuth<AlertPage>('/alerts?handled=false&page=1&size=1');
  return data.total ?? 0;
}

/** 获取库存列表（用于温区分布图） */
export async function fetchInventoryForChart(): Promise<InventoryItem[]> {
  const data = await fetchJsonWithAuth<InventoryPage>('/inventory/list?page=1&size=100');
  return data.records ?? [];
}

/** 获取待处理预警 Top5 */
export async function fetchTop5Alerts(): Promise<AlertRecord[]> {
  const data = await fetchJsonWithAuth<AlertPage>('/alerts?handled=false&page=1&size=5');
  return data.records ?? [];
}

// ── 批次详情模块 ────────────────────────────────────────

export interface LotDetail {
  lotNo: string;
  skuId: number;
  tempZone: string;
  produceDate: string;
  expireDate: string;
  initialQty: number;
  remainingQty: number;
  status: string;
  supplierId: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface TransferRecord {
  id: number;
  lotNo: string;
  type: string; // INBOUND / OUTBOUND / TRANSFER
  qty: number | null;
  fromLocation?: string | null;
  toLocation?: string | null;
  operator?: string | null;
  createdAt: string;
}

export async function fetchLotDetail(lotNo: string): Promise<LotDetail> {
  return fetchJsonWithAuth<LotDetail>(`/lots/${lotNo}`);
}

export async function fetchLotTransfers(lotNo: string): Promise<TransferRecord[]> {
  const data = await fetchJsonWithAuth<TransferRecord[] | { records: TransferRecord[] }>(
    `/lots/${lotNo}/transfers`
  );
  return Array.isArray(data) ? data : (data.records ?? []);
}

// ── 库存详情模块 ────────────────────────────────────────

export interface InventoryDetail {
  id: number;
  skuId: number;
  tempZone: string;
  totalQty: number;
  frozenQty: number;
  availableQty: number;
  version: number;
  updatedAt?: string;
}

export async function fetchInventoryDetail(
  skuId: number,
  tempZone: string
): Promise<InventoryDetail> {
  return fetchJsonWithAuth<InventoryDetail>(`/inventory/${skuId}/${tempZone}`);
}

export async function fetchInventoryLots(
  skuId: number,
  tempZone: string
): Promise<LotItem[]> {
  const data = await fetchJsonWithAuth<LotPage>(
    `/lots/list?skuId=${skuId}&tempZone=${encodeURIComponent(tempZone)}&page=1&size=100`
  );
  return data.records ?? [];
}

// ── AI 智能客服模块 ────────────────────────────────────

/** AI 对话模式（与后端 4 个端点对应，前端 3 chip 切换） */
export type AiMode = 'chat' | 'rag' | 'manus';

// RAG 流式问答由 AiChatPanel 通过 streamSse 直接调用
// GET /ai/chat/rag（SseEmitter 流式），无需同步封装。


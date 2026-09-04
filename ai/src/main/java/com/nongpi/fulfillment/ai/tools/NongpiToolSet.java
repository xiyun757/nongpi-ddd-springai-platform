package com.nongpi.fulfillment.ai.tools;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nongpi.fulfillment.alert.api.dto.AlertRecordResponse;
import com.nongpi.fulfillment.alert.application.AlertAppService;
import com.nongpi.fulfillment.alert.application.AlertQueryService;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.inventory.api.dto.InventoryDetailResponse;
import com.nongpi.fulfillment.inventory.application.InventoryAppService;
import com.nongpi.fulfillment.inventory.application.InventoryAppService.FreezeCommand;
import com.nongpi.fulfillment.inventory.application.InventoryQueryService;
import com.nongpi.fulfillment.inventory.domain.Inventory;
import com.nongpi.fulfillment.lot.api.dto.LotResponse;
import com.nongpi.fulfillment.lot.application.LotAppService;
import com.nongpi.fulfillment.lot.application.LotAppService.InboundCommand;
import com.nongpi.fulfillment.lot.application.LotAppService.OutboundCommand;
import com.nongpi.fulfillment.lot.application.LotQueryService;
import com.nongpi.fulfillment.lot.domain.Lot;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 农批智能客服 — 业务工具集（演示版）
 * <p>
 * 8 个 @Tool 覆盖 3 大业务域（lot/inventory/alert），含查询/写操作/跨域触发，
 * 足以演示 Manus 跨域多步规划能力。完整 14 个工具见设计文档 4.1 节，按需补齐。
 * </p>
 * <p>写操作工具通过 {@link #requireAdmin()} 校验当前 SecurityContext 中的角色，
 * 与项目 JWT 过滤器写入的 ROLE_ADMIN 权限一致。</p>
 */
@Component
public class NongpiToolSet {

    private final LotQueryService lotQueryService;
    private final InventoryQueryService inventoryQueryService;
    private final LotAppService lotAppService;
    private final InventoryAppService inventoryAppService;
    private final AlertQueryService alertQueryService;
    private final AlertAppService alertAppService;

    public NongpiToolSet(LotQueryService lotQueryService,
                         InventoryQueryService inventoryQueryService,
                         LotAppService lotAppService,
                         InventoryAppService inventoryAppService,
                         AlertQueryService alertQueryService,
                         AlertAppService alertAppService) {
        this.lotQueryService = lotQueryService;
        this.inventoryQueryService = inventoryQueryService;
        this.lotAppService = lotAppService;
        this.inventoryAppService = inventoryAppService;
        this.alertQueryService = alertQueryService;
        this.alertAppService = alertAppService;
    }

    // ── lot 域 ──────────────────────────────────────────────

    /**
     * 按批次号查询批次状态与剩余量（查询类，所有认证用户可用）
     */
    @Tool(description = "按批次号查询批次的当前状态、剩余数量、过期日期等详情。用于用户询问某批次情况时调用。")
    public LotResponse queryLotStatus(
            @ToolParam(description = "批次号，例如 LOT20260718001") String lotNo) {
        return lotQueryService.getDetail(lotNo);
    }

    /**
     * 批次入库（写操作，要求 ADMIN 角色）— 演示权限控制
     */
    @Tool(description = "创建新批次并入库：生成批次号、写入批次记录、同步库存。需要管理员权限。")
    public Lot inboundLot(
            @ToolParam(description = "SKU 编号") Long skuId,
            @ToolParam(description = "温区：FREEZE/FRESH/NORMAL") String tempZone,
            @ToolParam(description = "生产日期，格式 yyyy-MM-dd") String produceDate,
            @ToolParam(description = "过期日期，格式 yyyy-MM-dd") String expireDate,
            @ToolParam(description = "入库数量") String qty,
            @ToolParam(description = "供应商编号") Long supplierId) {
        ToolPermission.requireAdmin();
        InboundCommand cmd = new InboundCommand(
                skuId,
                TempZone.valueOf(tempZone.trim().toUpperCase()),
                java.time.LocalDate.parse(produceDate),
                java.time.LocalDate.parse(expireDate),
                new BigDecimal(qty.trim()),
                supplierId
        );
        return lotAppService.inbound(cmd);
    }

    /**
     * 批次出库（写操作，FEFO 策略，要求 ADMIN 角色）— 与入库对称
     * <p>注意：出库按 FEFO 策略自动选最早过期批次，不能指定特定 lotNo。
     * 如需操作指定批次，用 transferLot 转库工具。</p>
     */
    @Tool(description = "按 SKU 和温区执行出库操作（FEFO 先过期先出策略，系统自动选最早过期批次，不能指定批次号）。用于用户需要减少库存时调用。需要管理员权限。")
    public LotAppService.LotOutboundResult outboundLot(
            @ToolParam(description = "SKU 编号") Long skuId,
            @ToolParam(description = "温区：FREEZE/FRESH/NORMAL") String tempZone,
            @ToolParam(description = "出库数量") String qty,
            @ToolParam(description = "目标库位") String toLocation) {
        ToolPermission.requireAdmin();
        OutboundCommand cmd = new OutboundCommand(
                skuId,
                TempZone.valueOf(tempZone.trim().toUpperCase()),
                new BigDecimal(qty.trim()),
                toLocation,
                null // AI 工具不指定批次，走 FEFO 自动选最早过期批次
        );
        return lotAppService.outbound(cmd);
    }

    // ── inventory 域 ────────────────────────────────────────

    /**
     * 按 SKU + 温区查询库存可用量（查询类，跨库存域演示）
     */
    @Tool(description = "按 SKU 编号和温区查询库存详情：总量、可用量、冻结量。用于用户询问某商品库存时调用。")
    public InventoryDetailResponse queryInventory(
            @ToolParam(description = "SKU 编号") Long skuId,
            @ToolParam(description = "温区，可选值：FREEZE(冷冻)/FRESH(冷藏)/NORMAL(常温)") String tempZone) {
        // 校验温区合法性（非法值抛 IllegalArgumentException，LLM 据此修正）
        String zone = TempZone.valueOf(tempZone.trim().toUpperCase()).name();
        return inventoryQueryService.getDetail(skuId, zone);
    }

    /**
     * 冻结库存（写操作，要求 ADMIN 角色）— 演示库存状态机
     */
    @Tool(description = "冻结指定 SKU 和温区的库存：将可用量转为冻结量。用于用户需要预留库存时调用。需要管理员权限。")
    public Inventory freezeStock(
            @ToolParam(description = "SKU 编号") Long skuId,
            @ToolParam(description = "温区：FREEZE/FRESH/NORMAL") String tempZone,
            @ToolParam(description = "冻结数量") String qty) {
        ToolPermission.requireAdmin();
        FreezeCommand cmd = new FreezeCommand(
                skuId,
                TempZone.valueOf(tempZone.trim().toUpperCase()),
                new BigDecimal(qty.trim())
        );
        return inventoryAppService.freeze(cmd);
    }

    // ── alert 域（跨域触发，演示 Manus 多步规划）─────────────

    /**
     * 预警记录分页查询（查询类）
     */
    @Tool(description = "分页查询预警记录，支持按处理状态、预警级别、批次号筛选。用于用户查看预警历史时调用。")
    public IPage<AlertRecordResponse> listAlertRecords(
            @ToolParam(description = "是否已处理：true=已处理，false=未处理，留空=全部", required = false) Boolean handled,
            @ToolParam(description = "预警级别：CRITICAL/WARNING/INFO，留空=全部", required = false) String alertLevel,
            @ToolParam(description = "批次号模糊匹配，留空=不筛选", required = false) String lotNo,
            @ToolParam(description = "页码，从1开始", required = false) Integer page,
            @ToolParam(description = "每页条数", required = false) Integer size) {
        return alertQueryService.listRecords(
                handled,
                alertLevel,
                lotNo,
                null, null,
                page == null ? 1 : page,
                size == null ? 10 : size
        );
    }

    /**
     * 临期批次检查（写操作，跨域触发：alert → lot，演示 Manus 多步规划）
     * <p>返回结构化预警详情，让 LLM 能链式调用 freezeStock 冻结对应库存。
     * 原 int 返回值 LLM 无法据此决定冻结哪个 SKU，导致链路断裂。</p>
     */
    @Tool(description = "扫描所有在库批次，按已启用的预警规则检查临期情况并生成预警记录。返回新增的未处理预警记录列表（含 lotNo/skuId/tempZone/alertLevel/message），便于后续对每条预警调用 freezeStock 冻结对应 SKU+温区的库存。需要管理员权限。")
    public List<AlertRecordResponse> checkExpiringLots() {
        ToolPermission.requireAdmin();
        int count = alertAppService.checkExpiringLots();
        if (count == 0) {
            return List.of();
        }
        // 拉取未处理预警（取前 50 条），把结构化详情返回给 LLM，
        // 否则 LLM 无法据此决定 freezeStock 的 skuId/tempZone，链路断裂。
        return alertQueryService.listRecords(false, null, null, null, null, 1, 50).getRecords();
    }

    // ── 权限校验 ────────────────────────────────────────────
    // 写操作工具统一委托 ToolPermission.requireAdmin()，避免权限校验逻辑散落多处。
    // 复用 tools/ToolPermission.java 公共助手，原 NongpiToolSet.requireAdmin() 私有方法已删除。
}

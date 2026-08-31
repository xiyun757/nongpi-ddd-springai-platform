package com.nongpi.fulfillment.alert.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nongpi.fulfillment.alert.api.dto.AlertRecordResponse;
import com.nongpi.fulfillment.alert.api.dto.AlertRuleResponse;
import com.nongpi.fulfillment.alert.application.AlertAppService;
import com.nongpi.fulfillment.alert.application.AlertAppService.CreateRuleCommand;
import com.nongpi.fulfillment.alert.application.AlertQueryService;
import com.nongpi.fulfillment.alert.domain.AlertRecord;
import com.nongpi.fulfillment.alert.domain.AlertRule;
import com.nongpi.fulfillment.common.domain.AlertLevel;
import com.nongpi.fulfillment.common.domain.TempZone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * 预警 REST 控制器
 *
 * <p>提供预警记录查询、处理、规则管理接口。
 * 查询委托 {@link AlertQueryService}，写操作委托 {@link AlertAppService}，
 * Controller 自身不再直接注入 Mapper，符合 DDD 分层。</p>
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertAppService alertAppService;
    private final AlertQueryService alertQueryService;

    public AlertController(AlertAppService alertAppService,
                           AlertQueryService alertQueryService) {
        this.alertAppService = alertAppService;
        this.alertQueryService = alertQueryService;
    }

    /**
     * 预警记录分页列表（支持 handled、alertLevel 筛选）
     */
    @GetMapping
    public IPage<AlertRecordResponse> list(
            @RequestParam(required = false) Boolean handled,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String lotNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        return alertQueryService.listRecords(handled, alertLevel, lotNo,
                startDate, endDate, page, size);
    }

    /**
     * 处理预警
     */
    @PostMapping("/{id}/handle")
    public AlertRecordDetailResponse handle(@PathVariable Long id,
                                      @Valid @RequestBody HandleRequest req) {
        AlertRecord record = alertAppService.handleAlert(id, req.handler());
        return toRecordResponse(record);
    }

    /**
     * 手动触发临期检查
     */
    @PostMapping("/check")
    public CheckResultResponse check() {
        int count = alertAppService.checkExpiringLots();
        return new CheckResultResponse(count);
    }

    /**
     * 创建预警规则
     */
    @PostMapping("/rules")
    public AlertRuleResponse createRule(@Valid @RequestBody CreateRuleRequest req) {
        CreateRuleCommand cmd = new CreateRuleCommand(
                req.skuId(), req.tempZone(), req.thresholdDays(), req.alertLevel());
        AlertRule rule = alertAppService.createRule(cmd);
        // 重新查询以获取 createdAt
        return alertQueryService.getRuleById(rule.getId());
    }

    /**
     * 查询预警规则列表（支持 skuId / tempZone / enabled 筛选）
     */
    @GetMapping("/rules")
    public List<AlertRuleResponse> listRules(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String tempZone,
            @RequestParam(required = false) Boolean enabled) {
        return alertQueryService.listRules(skuId, tempZone, enabled);
    }

    /**
     * 删除预警规则
     */
    @DeleteMapping("/rules/{id}")
    public void deleteRule(@PathVariable Long id) {
        alertAppService.deleteRule(id);
    }

    /**
     * 启用/禁用预警规则
     */
    @PutMapping("/rules/{id}/toggle")
    public AlertRuleResponse toggleRule(@PathVariable Long id,
                                        @Valid @RequestBody ToggleRuleRequest req) {
        AlertRule rule = alertAppService.toggleRule(id, req.enabled());
        return alertQueryService.getRuleById(rule.getId());
    }

    // ── 响应 / 请求 DTO ──────────────────────────────────────

    /** 预警详情响应（含 id 字段，前端处理时需要） */
    public record AlertRecordDetailResponse(
            Long id, String lotNo, Long alertRuleId, String alertLevel,
            String message, boolean handled, String handler,
            String handledAt, String createdAt
    ) {}

    public record CheckResultResponse(int newAlertCount) {}

    public record HandleRequest(@NotBlank(message = "处理人不能为空") String handler) {}

    public record CreateRuleRequest(
            Long skuId,
            TempZone tempZone,
            @NotNull(message = "阈值天数不能为空") @Min(value = 1, message = "阈值天数必须大于0") int thresholdDays,
            @NotNull(message = "预警级别不能为空") AlertLevel alertLevel
    ) {}

    public record ToggleRuleRequest(boolean enabled) {}

    // ── 转换方法 ────────────────────────────────────────────

    private AlertRecordDetailResponse toRecordResponse(AlertRecord record) {
        return new AlertRecordDetailResponse(
                record.getId(),
                record.getLotNo(),
                record.getAlertRuleId(),
                record.getAlertLevel().name(),
                record.getMessage(),
                record.isHandled(),
                record.getHandler(),
                record.getHandledAt() != null ? record.getHandledAt().toString() : null,
                record.getCreatedAt() != null ? record.getCreatedAt().toString() : null
        );
    }
}

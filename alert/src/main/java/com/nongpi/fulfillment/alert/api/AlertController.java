package com.nongpi.fulfillment.alert.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nongpi.fulfillment.alert.api.dto.AlertRecordDetailResponse;
import com.nongpi.fulfillment.alert.api.dto.AlertRecordResponse;
import com.nongpi.fulfillment.alert.api.dto.AlertRuleResponse;
import com.nongpi.fulfillment.alert.api.dto.CheckResultResponse;
import com.nongpi.fulfillment.alert.api.dto.CreateRuleRequest;
import com.nongpi.fulfillment.alert.api.dto.HandleRequest;
import com.nongpi.fulfillment.alert.api.dto.ToggleRuleRequest;
import com.nongpi.fulfillment.alert.application.AlertAppService;
import com.nongpi.fulfillment.alert.application.AlertAppService.CreateRuleCommand;
import com.nongpi.fulfillment.alert.application.AlertQueryService;
import com.nongpi.fulfillment.alert.domain.AlertRecord;
import com.nongpi.fulfillment.alert.domain.AlertRule;
import jakarta.validation.Valid;
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

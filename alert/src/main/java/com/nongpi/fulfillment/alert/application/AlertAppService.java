package com.nongpi.fulfillment.alert.application;

import com.nongpi.fulfillment.alert.domain.AlertRecord;
import com.nongpi.fulfillment.alert.domain.AlertRule;
import com.nongpi.fulfillment.alert.domain.IAlertRecordRepository;
import com.nongpi.fulfillment.alert.domain.IAlertRuleRepository;
import com.nongpi.fulfillment.common.domain.AlertLevel;
import com.nongpi.fulfillment.common.domain.LotStatus;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.lot.domain.ILotRepository;
import com.nongpi.fulfillment.lot.domain.Lot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 预警应用服务 — 用例编排层
 *
 * <p>编排临期批次检查、预警记录生成、预警处理等业务用例。</p>
 */
@Service
public class AlertAppService {

    private static final Logger log = LoggerFactory.getLogger(AlertAppService.class);

    private final IAlertRuleRepository alertRuleRepository;
    private final IAlertRecordRepository alertRecordRepository;
    private final ILotRepository lotRepository;

    public AlertAppService(IAlertRuleRepository alertRuleRepository,
                           IAlertRecordRepository alertRecordRepository,
                           ILotRepository lotRepository) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertRecordRepository = alertRecordRepository;
        this.lotRepository = lotRepository;
    }

    /**
     * 检查临期批次并生成预警记录
     *
     * <p>遍历所有已启用的预警规则，对每条规则匹配的批次检查是否即将过期，
     * 若未处理过同类预警则生成新记录。</p>
     *
     * @return 新生成的预警记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int checkExpiringLots() {
        List<AlertRule> rules = alertRuleRepository.findAllEnabled();
        if (rules.isEmpty()) {
            log.debug("无已启用的预警规则，跳过检查");
            return 0;
        }

        // 查询所有在库且未过期的批次（阈值取规则中最大的 thresholdDays）
        int maxThreshold = rules.stream()
                .mapToInt(AlertRule::getThresholdDays)
                .max()
                .orElse(0);
        LocalDate cutoffDate = LocalDate.now().plusDays(maxThreshold);
        List<Lot> candidateLots = lotRepository.findByExpireDateBefore(cutoffDate);

        int alertCount = 0;
        for (Lot lot : candidateLots) {
            // 跳过已过期或已出清的批次
            if (lot.getStatus() == LotStatus.EXPIRED || lot.getStatus() == LotStatus.FULLY_OUT) {
                continue;
            }

            for (AlertRule rule : rules) {
                // 规则是否适用于此批次
                if (!rule.matches(lot.getSkuId(), lot.getTempZone())) {
                    continue;
                }
                // 是否触发预警
                if (!rule.shouldAlert(lot.getExpireDate())) {
                    continue;
                }
                // 生成预警记录（原子幂等：INSERT ... WHERE NOT EXISTS 防止并发 TOCTOU 竞态）
                long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), lot.getExpireDate());
                String message = buildAlertMessage(lot, rule, daysLeft);
                AlertRecord record = AlertRecord.create(
                        lot.getLotNo().value(),
                        rule.getId(),
                        rule.getAlertLevel(),
                        message
                );
                boolean inserted = alertRecordRepository.saveIfNotExists(record);
                if (inserted) {
                    alertCount++;
                }
            }
        }

        if (alertCount > 0) {
            log.info("临期检查完成，新增 {} 条预警记录", alertCount);
        }
        return alertCount;
    }

    /**
     * 每天凌晨 2 点自动检查临期批次
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCheckExpiringLots() {
        log.info("定时任务触发：开始临期批次检查");
        checkExpiringLots();
    }

    /**
     * 处理预警
     *
     * @param alertId 预警记录 ID
     * @param handlerName 处理人
     * @return 处理后的预警记录
     */
    @Transactional(rollbackFor = Exception.class)
    public AlertRecord handleAlert(Long alertId, String handlerName) {
        AlertRecord record = alertRecordRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("预警记录不存在: " + alertId));
        record.handle(handlerName);
        alertRecordRepository.update(record);
        return record;
    }

    /**
     * 创建预警规则
     */
    @Transactional(rollbackFor = Exception.class)
    public AlertRule createRule(CreateRuleCommand cmd) {
        AlertRule rule = AlertRule.create(cmd.skuId(), cmd.tempZone(),
                cmd.thresholdDays(), cmd.alertLevel());
        alertRuleRepository.save(rule);
        return rule;
    }

    /**
     * 启用/禁用预警规则
     */
    @Transactional(rollbackFor = Exception.class)
    public AlertRule toggleRule(Long ruleId, boolean enabled) {
        AlertRule rule = alertRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("预警规则不存在: " + ruleId));
        if (enabled) {
            rule.enable();
        } else {
            rule.disable();
        }
        alertRuleRepository.update(rule);
        return rule;
    }

    /**
     * 删除预警规则
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long ruleId) {
        alertRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("预警规则不存在: " + ruleId));
        alertRuleRepository.deleteById(ruleId);
    }

    // ── 内部方法 ────────────────────────────────────────────

    private String buildAlertMessage(Lot lot, AlertRule rule, long daysLeft) {
        return String.format("批次【%s】将在 %d 天后过期（过期日期：%s），" +
                        "SKU=%d，温区=%s，预警级别=%s，规则阈值=%d天",
                lot.getLotNo().value(), daysLeft, lot.getExpireDate(),
                lot.getSkuId(), lot.getTempZone(), rule.getAlertLevel(), rule.getThresholdDays());
    }

    // ── 命令 Records ────────────────────────────────────────

    /**
     * 创建预警规则命令
     *
     * @param skuId         SKU 标识（null=全局）
     * @param tempZone      温区（null=所有温区）
     * @param thresholdDays 过期警戒天数
     * @param alertLevel    预警级别
     */
    public record CreateRuleCommand(Long skuId, TempZone tempZone,
                                    int thresholdDays, AlertLevel alertLevel) {}
}

package com.nongpi.fulfillment.lot.infrastructure;

import com.nongpi.fulfillment.common.domain.LotStatus;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.lot.domain.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库兜底 FEFO 查询策略
 * <p>当 Redis 策略不可用时，直接查询 t_lot 表按 expireDate 排序。
 * 作为 {@link RedisFefoStrategy} 的降级方案，保证功能不丢。</p>
 */
@Component
public class DatabaseFefoStrategy implements FefoStrategy {

    private static final int FALLBACK_DAYS = 999;

    private final ILotRepository repository;

    public DatabaseFefoStrategy(ILotRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<LotNo> getEarliest(TempZone zone, BigDecimal qty, Long skuId) {
        LocalDate today = LocalDate.now();
        //candidates符合要求的批次集合
        List<Lot> candidates = repository.findByTempZoneAndExpireDateBefore(zone, today.plusDays(FALLBACK_DAYS))
                .stream()
                .filter(l -> l.getStatus() == LotStatus.IN_STOCK
                        || l.getStatus() == LotStatus.PARTIAL_OUT)
                .filter(l -> skuId == null || java.util.Objects.equals(l.getSkuId(), skuId))
                .filter(l -> !l.getExpireDate().isBefore(today))
                .toList();
        //accumulated一开始为0，直到满足出库条件（accumulated.compareTo(qty) >= 0
        BigDecimal accumulated = BigDecimal.ZERO;
        List<LotNo> result = new ArrayList<>();
        for (Lot lot : candidates) {
            result.add(lot.getLotNo());
            accumulated = accumulated.add(lot.getRemainingQty());
            if (accumulated.compareTo(qty) >= 0) {
                break;
            }
        }

        if (result.isEmpty()) {
            throw new NoAvailableLotException("温区 " + zone + " 没有可用的批次");
        }
        return result;
    }
}

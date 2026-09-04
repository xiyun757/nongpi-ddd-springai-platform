package com.nongpi.fulfillment.sku.application;

import com.nongpi.fulfillment.common.exception.NotFoundException;
import com.nongpi.fulfillment.sku.domain.ISkuRepository;
import com.nongpi.fulfillment.sku.domain.Sku;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品应用服务 — 主数据写操作（新建/删除）
 */
@Service
public class SkuAppService {

    private final ISkuRepository repository;

    public SkuAppService(ISkuRepository repository) {
        this.repository = repository;
    }

    /**
     * 新建商品
     */
    @Transactional(rollbackFor = Exception.class)
    public Sku create(CreateSkuCommand cmd) {
        Sku sku = Sku.createNew(cmd.name(), cmd.spec(), cmd.unit(), cmd.barcode());
        repository.save(sku);
        return sku;
    }

    /**
     * 删除商品 — 业务上仅允许删除未被引用的商品（t_lot / t_inventory 引用由外部调用方校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new NotFoundException("SKU_NOT_FOUND", "商品不存在: " + id);
        }
        repository.deleteById(id);
    }

    /**
     * 新建商品命令
     */
    public record CreateSkuCommand(String name, String spec, String unit, String barcode) {}
}

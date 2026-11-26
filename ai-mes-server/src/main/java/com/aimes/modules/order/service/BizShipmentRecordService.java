package com.aimes.modules.order.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.entity.BizShipmentRecord;
import com.aimes.modules.order.mapper.BizShipmentRecordMapper;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BizShipmentRecordService extends ServiceImpl<BizShipmentRecordMapper, BizShipmentRecord> {

    private final BizSalesOrderItemMapper orderItemMapper;
    private final BizSalesOrderService salesOrderService;
    private final InventoryService inventoryService;
    private final BizMaterialMapper materialMapper;

    public List<BizShipmentRecord> getByOrderId(Long orderId) {
        return list(new LambdaQueryWrapper<BizShipmentRecord>()
                .eq(BizShipmentRecord::getOrderId, orderId)
                .orderByDesc(BizShipmentRecord::getCreateTime));
    }

    @Transactional
    public void createShipment(BizShipmentRecord record) {
        record.setShipmentNo(CodeUtil.generate("SH"));
        if (record.getShippedAt() == null) record.setShippedAt(LocalDateTime.now());
        if (record.getStatus() == null) record.setStatus("shipped");
        save(record);

        // 1. 更新订单明细的已发货数量
        if (record.getOrderItemId() != null) {
            BizSalesOrderItem item = orderItemMapper.selectById(record.getOrderItemId());
            if (item != null) {
                BigDecimal currentShipped = item.getShippedQty() != null ? item.getShippedQty() : BigDecimal.ZERO;
                item.setShippedQty(currentShipped.add(record.getShippedQty()));
                orderItemMapper.updateById(item);
            }
        }

        // 2. 库存出库：发货即减库存
        if (record.getProductId() != null && record.getShippedQty() != null
                && record.getShippedQty().compareTo(BigDecimal.ZERO) > 0) {
            try {
                BizMaterial product = materialMapper.selectById(record.getProductId());
                Long warehouseId = product != null && product.getDefaultWarehouseId() != null
                        ? product.getDefaultWarehouseId() : null;
                if (warehouseId != null) {
                    inventoryService.stockOut(record.getProductId(), warehouseId,
                            record.getShippedQty(), "sales_out", null,
                            "sales_order", record.getOrderId());
                }
            } catch (Exception e) {
                log.error("发货库存出库失败: shipmentNo={}, productId={}", record.getShipmentNo(), record.getProductId(), e);
            }
        }

        // 3. 应收账款已改为在库存任务处理(process)时按实际出库数量*单价生成，此处不再自动创建

        // 4. 更新订单发货状态（partial_shipped / shipped）
        if (record.getOrderId() != null) {
            try {
                salesOrderService.updateOrderShipmentStatus(record.getOrderId());
            } catch (Exception e) {
                log.error("更新订单发货状态失败: orderId={}", record.getOrderId(), e);
            }
        }
    }

    public void deleteShipment(Long id) {
        BizShipmentRecord record = getById(id);
        if (record == null) throw new BusinessException("发货记录不存在");
        removeById(id);
    }
}

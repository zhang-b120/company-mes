package com.aimes.modules.order.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.finance.entity.BizFinanceInvoice;
import com.aimes.modules.finance.mapper.BizFinanceInvoiceMapper;
import com.aimes.modules.master.entity.BizCustomer;
import com.aimes.modules.master.mapper.BizCustomerMapper;
import com.aimes.modules.order.entity.BizOrderInvoice;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.mapper.BizOrderInvoiceMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BizOrderInvoiceService extends ServiceImpl<BizOrderInvoiceMapper, BizOrderInvoice> {

    private final BizFinanceInvoiceMapper financeInvoiceMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizCustomerMapper customerMapper;

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.url-prefix}")
    private String uploadUrlPrefix;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public List<BizOrderInvoice> getByOrderId(Long orderId) {
        return list(new LambdaQueryWrapper<BizOrderInvoice>()
                .eq(BizOrderInvoice::getOrderId, orderId)
                .orderByDesc(BizOrderInvoice::getCreateTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public void createInvoice(BizOrderInvoice invoice) {
        if (invoice.getStatus() == null) invoice.setStatus("issued");
        save(invoice);
        // 同步到财务发票表
        syncToFinanceInvoice(invoice);
    }

    /** 将订单发票同步到财务发票表,记录 relatedId=BizOrderInvoice.id 以便状态双向同步 */
    private void syncToFinanceInvoice(BizOrderInvoice src) {
        try {
            BizSalesOrder order = src.getOrderId() != null ? salesOrderMapper.selectById(src.getOrderId()) : null;
            BizFinanceInvoice fi = new BizFinanceInvoice();
            fi.setInvoiceNo(com.aimes.common.utils.CodeUtil.generate("INV"));
            fi.setInvoiceType("output");      // 销项(销售开给客户)
            fi.setInvoiceNumber(src.getInvoiceNo());
            fi.setInvoiceDate(src.getInvoiceDate());
            // 财务表: amount=不含税, taxAmount=税额, totalAmount=价税合计
            BigDecimal total = src.getInvoiceAmount() != null ? src.getInvoiceAmount() : BigDecimal.ZERO;
            BigDecimal tax = src.getTaxAmount() != null ? src.getTaxAmount() : BigDecimal.ZERO;
            fi.setAmount(total.subtract(tax));
            fi.setTaxRate(src.getTaxRate() != null ? src.getTaxRate() : new BigDecimal("13"));
            fi.setTaxAmount(tax);
            fi.setTotalAmount(total);
            fi.setAttachmentUrl(src.getAttachmentUrl());
            fi.setItemsJson(src.getItemsJson());
            fi.setRelatedType("sales_order");
            fi.setRelatedId(src.getId());     // 关联到本 BizOrderInvoice.id,用于反查和状态同步
            fi.setRelatedNo(order != null ? order.getOrderNo() : null);
            fi.setCustomerId(order != null ? order.getCustomerId() : null);
            // 购方默认取客户名称
            if (order != null && order.getCustomerId() != null) {
                BizCustomer cust = customerMapper.selectById(order.getCustomerId());
                if (cust != null) {
                    fi.setBuyerName(cust.getCustomerName());
                }
            }
            // 映射状态: issued→confirmed, cancelled→cancelled, pending→draft
            fi.setStatus(mapStatus(src.getStatus()));
            fi.setRemark(src.getRemark());
            financeInvoiceMapper.insert(fi);
        } catch (Exception e) {
            log.warn("同步订单发票到财务发票失败: orderInvoiceId={}", src.getId(), e);
        }
    }

    private String mapStatus(String orderInvoiceStatus) {
        if ("issued".equals(orderInvoiceStatus)) return "confirmed";
        if ("cancelled".equals(orderInvoiceStatus)) return "cancelled";
        return "draft";
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateInvoice(Long id, BizOrderInvoice invoice) {
        BizOrderInvoice existing = getById(id);
        if (existing == null) throw new BusinessException("发票记录不存在");
        invoice.setId(id);
        updateById(invoice);
        // 同步更新财务发票的状态、金额
        try {
            BizFinanceInvoice fi = financeInvoiceMapper.selectOne(
                    new LambdaQueryWrapper<BizFinanceInvoice>()
                            .eq(BizFinanceInvoice::getRelatedType, "sales_order")
                            .eq(BizFinanceInvoice::getRelatedId, id)
                            .eq(BizFinanceInvoice::getDeleted, 0)
                            .last("LIMIT 1"));
            if (fi != null) {
                BizOrderInvoice updated = getById(id);
                if (updated.getStatus() != null) fi.setStatus(mapStatus(updated.getStatus()));
                if (updated.getInvoiceAmount() != null) {
                    BigDecimal tax = updated.getTaxAmount() != null ? updated.getTaxAmount() : BigDecimal.ZERO;
                    fi.setTotalAmount(updated.getInvoiceAmount());
                    fi.setTaxAmount(tax);
                    fi.setAmount(updated.getInvoiceAmount().subtract(tax));
                }
                if (updated.getInvoiceDate() != null) fi.setInvoiceDate(updated.getInvoiceDate());
                if (updated.getAttachmentUrl() != null) fi.setAttachmentUrl(updated.getAttachmentUrl());
                if (updated.getItemsJson() != null) fi.setItemsJson(updated.getItemsJson());
                if (updated.getRemark() != null) fi.setRemark(updated.getRemark());
                financeInvoiceMapper.updateById(fi);
            }
        } catch (Exception e) {
            log.warn("同步更新财务发票失败: orderInvoiceId={}", id, e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteInvoice(Long id) {
        BizOrderInvoice existing = getById(id);
        if (existing == null) throw new BusinessException("发票记录不存在");
        removeById(id);
        // 同步软删除财务发票
        try {
            BizFinanceInvoice fi = financeInvoiceMapper.selectOne(
                    new LambdaQueryWrapper<BizFinanceInvoice>()
                            .eq(BizFinanceInvoice::getRelatedType, "sales_order")
                            .eq(BizFinanceInvoice::getRelatedId, id)
                            .eq(BizFinanceInvoice::getDeleted, 0)
                            .last("LIMIT 1"));
            if (fi != null) financeInvoiceMapper.deleteById(fi.getId());
        } catch (Exception e) {
            log.warn("同步删除财务发票失败: orderInvoiceId={}", id, e);
        }
    }

    /**
     * 上传发票附件，返回 fileUrl 和 originalName
     */
    public Map<String, Object> uploadFile(MultipartFile file) throws IOException {
        String rawName = file.getOriginalFilename();
        String originalName = (rawName != null && !rawName.isBlank()) ? rawName : "unknown";
        String ext = "";
        if (rawName != null) {
            int dotIdx = rawName.lastIndexOf('.');
            if (dotIdx >= 0) ext = rawName.substring(dotIdx);
        }

        String timestamp = LocalDateTime.now().format(TS_FMT);
        String serverName = "INV_" + timestamp + ext;

        File dir = new File(uploadPath + "/invoice-attachments");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("发票附件目录创建失败");
        }

        File dest = new File(dir, serverName);
        file.transferTo(dest);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileUrl", uploadUrlPrefix + "/invoice-attachments/" + serverName);
        result.put("originalName", originalName);
        return result;
    }
}

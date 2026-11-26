package com.aimes.modules.purchase.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.entity.BizPurchaseRequest;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseRequestMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.service.SysBizConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseRequestService extends ServiceImpl<BizPurchaseRequestMapper, BizPurchaseRequest> {

    private final BizSupplierMapper supplierMapper;
    private final BizMaterialMapper materialMapper;
    private final BizPurchaseOrderItemMapper orderItemMapper;
    private final SysBizConfigService bizConfigService;
    private final SysUserMapper userMapper;
    @Lazy
    private final PurchaseOrderService orderService;

    /**
     * 分页查询采购申请（支持复杂查询）
     */
    public PageResult<Map<String, Object>> getPage(String status,
                                                    String requestNo, String materialName,
                                                    String sourceType, Long suggestedSupplierId,
                                                    LocalDate expectedDateStart, LocalDate expectedDateEnd,
                                                    LocalDateTime createTimeStart, LocalDateTime createTimeEnd,
                                                    int pageNum, int pageSize) {
        LambdaQueryWrapper<BizPurchaseRequest> wrapper = new LambdaQueryWrapper<BizPurchaseRequest>()
                .like(StringUtils.hasText(requestNo), BizPurchaseRequest::getRequestNo, requestNo)
                .like(StringUtils.hasText(materialName), BizPurchaseRequest::getMaterialName, materialName)
                .eq(StringUtils.hasText(status), BizPurchaseRequest::getStatus, status)
                .eq(StringUtils.hasText(sourceType), BizPurchaseRequest::getSourceType, sourceType)
                .eq(suggestedSupplierId != null, BizPurchaseRequest::getSuggestedSupplierId, suggestedSupplierId)
                .ge(expectedDateStart != null, BizPurchaseRequest::getExpectedDate, expectedDateStart)
                .le(expectedDateEnd != null, BizPurchaseRequest::getExpectedDate, expectedDateEnd)
                .ge(createTimeStart != null, BizPurchaseRequest::getCreateTime, createTimeStart)
                .le(createTimeEnd != null, BizPurchaseRequest::getCreateTime, createTimeEnd)
                .orderByDesc(BizPurchaseRequest::getCreateTime);

        Page<BizPurchaseRequest> page = page(new Page<>(pageNum, pageSize), wrapper);

        List<BizPurchaseRequest> records = page.getRecords();
        Set<Long> supplierIds = records.stream().map(BizPurchaseRequest::getSuggestedSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> supplierNameMap = supplierIds.isEmpty() ? new HashMap<>() :
                supplierMapper.selectBatchIds(supplierIds).stream().collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        Set<Long> materialIds = records.stream().map(BizPurchaseRequest::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> materialMap = materialIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(materialIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        Set<Long> approverIds = records.stream().map(BizPurchaseRequest::getApprovedBy).filter(Objects::nonNull).collect(Collectors.toSet());
        // 合并创建人ID用于查询申请人名称
        Set<Long> creatorIds = records.stream().map(BizPurchaseRequest::getCreateBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> allUserIds = new HashSet<>(approverIds);
        allUserIds.addAll(creatorIds);
        Map<Long, String> userNameMap = allUserIds.isEmpty() ? new HashMap<>() :
                userMapper.selectBatchIds(allUserIds).stream().collect(Collectors.toMap(SysUser::getId, u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));
        Map<Long, String> approverNameMap = userNameMap;

        List<Map<String, Object>> list = records.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            BizMaterial mat = materialMap.get(r.getMaterialId());
            map.put("id", r.getId());
            map.put("requestNo", r.getRequestNo());
            map.put("materialId", r.getMaterialId());
            map.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            map.put("materialName", StringUtils.hasText(r.getMaterialName()) ? r.getMaterialName() : (mat != null ? mat.getMaterialName() : ""));
            map.put("spec", StringUtils.hasText(r.getSpec()) ? r.getSpec() : (mat != null ? mat.getSpec() : ""));
            map.put("unit", StringUtils.hasText(r.getUnit()) ? r.getUnit() : (mat != null ? mat.getUnit() : ""));
            map.put("quantity", r.getQuantity());
            map.put("suggestedSupplierId", r.getSuggestedSupplierId());
            map.put("supplierName", r.getSuggestedSupplierId() != null ? supplierNameMap.getOrDefault(r.getSuggestedSupplierId(), "") : null);
            map.put("expectedDate", r.getExpectedDate());
            map.put("requiredDate", r.getExpectedDate());
            map.put("sourceType", r.getSourceType());
            map.put("sourceNo", r.getSourceNo());
            map.put("purpose", r.getPurpose());
            map.put("estimatedPrice", r.getEstimatedPrice());
            map.put("status", r.getStatus());
            map.put("approvedBy", r.getApprovedBy());
            map.put("approverName", r.getApprovedBy() != null ? approverNameMap.getOrDefault(r.getApprovedBy(), "") : null);
            map.put("approvedAt", r.getApprovedAt());
            map.put("approveRemark", r.getApproveRemark());
            map.put("remark", r.getRemark());
            map.put("applicantName", r.getCreateBy() != null ? userNameMap.getOrDefault(r.getCreateBy(), "") : "");
            map.put("createTime", r.getCreateTime());
            map.put("updateTime", r.getUpdateTime());
            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 查询单条采购申请详情
     */
    public Map<String, Object> getDetail(Long id) {
        BizPurchaseRequest r = getById(id);
        if (r == null) throw new BusinessException("采购申请不存在");

        BizMaterial mat = r.getMaterialId() != null ? materialMapper.selectById(r.getMaterialId()) : null;
        String supplierName = "";
        if (r.getSuggestedSupplierId() != null) {
            BizSupplier sup = supplierMapper.selectById(r.getSuggestedSupplierId());
            if (sup != null) supplierName = sup.getSupplierName();
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.getId());
        map.put("requestNo", r.getRequestNo());
        map.put("materialId", r.getMaterialId());
        map.put("materialNo", mat != null ? mat.getMaterialNo() : "");
        map.put("materialName", StringUtils.hasText(r.getMaterialName()) ? r.getMaterialName() : (mat != null ? mat.getMaterialName() : ""));
        map.put("spec", StringUtils.hasText(r.getSpec()) ? r.getSpec() : (mat != null ? mat.getSpec() : ""));
        map.put("unit", StringUtils.hasText(r.getUnit()) ? r.getUnit() : (mat != null ? mat.getUnit() : ""));
        map.put("quantity", r.getQuantity());
        map.put("suggestedSupplierId", r.getSuggestedSupplierId());
        map.put("suggestedSupplierName", supplierName);
        map.put("expectedDate", r.getExpectedDate());
        map.put("requiredDate", r.getExpectedDate());
        map.put("sourceType", r.getSourceType());
        map.put("sourceNo", r.getSourceNo());
        map.put("purpose", r.getPurpose());
        map.put("estimatedPrice", r.getEstimatedPrice());
        map.put("status", r.getStatus());
        map.put("approvedBy", r.getApprovedBy());
        map.put("approvedAt", r.getApprovedAt());
        map.put("approveRemark", r.getApproveRemark());
        map.put("remark", r.getRemark());
        map.put("createTime", r.getCreateTime());
        return map;
    }

    /**
     * 创建采购申请
     */
    public void create(BizPurchaseRequest request) {
        // 支持自定义申请单号：填写则校验唯一性，否则自动生成
        if (StringUtils.hasText(request.getRequestNo())) {
            long count = count(new LambdaQueryWrapper<BizPurchaseRequest>()
                    .eq(BizPurchaseRequest::getRequestNo, request.getRequestNo().trim()));
            if (count > 0) throw new BusinessException("采购申请单号[" + request.getRequestNo() + "]已存在，请使用其他单号");
            request.setRequestNo(request.getRequestNo().trim());
        } else {
            request.setRequestNo(CodeUtil.generate("PR"));
        }
        // 根据配置决定采购申请是否需要审批
        // need_approval=true(默认): 提交后状态为pending，等待审批
        // need_approval=false: 直接跳过审批，状态为approved，可直接下发采购订单
        boolean needApproval = bizConfigService.getBoolValue("purchase.request.need_approval", true);
        request.setStatus(needApproval ? "pending" : "approved");
        // 自动填充物料名称/规格/单位
        if (request.getMaterialId() != null) {
            BizMaterial material = materialMapper.selectById(request.getMaterialId());
            if (material != null) {
                if (!StringUtils.hasText(request.getMaterialName())) {
                    request.setMaterialName(material.getMaterialName());
                }
                if (!StringUtils.hasText(request.getSpec())) {
                    request.setSpec(material.getSpec());
                }
                if (!StringUtils.hasText(request.getUnit())) {
                    request.setUnit(material.getUnit());
                }
            }
        }
        save(request);
    }

    /**
     * 独立事务创建采购申请: 给"调用方事务可能回滚, 但 PR 必须落盘"的场景用.
     * 例: 工单领料缺料时, 父方法会 throw BusinessException 回滚领料动作,
     *     但自动生成的采购申请要保留下来让采购员处理.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createInNewTransaction(BizPurchaseRequest request) {
        create(request);
    }

    /** 直接创建草稿状态的采购申请(给自动生成场景用, 跳过审批配置, 用户在 UI 上编辑后再 submit) */
    public void createAsDraft(BizPurchaseRequest request) {
        if (StringUtils.hasText(request.getRequestNo())) {
            long count = count(new LambdaQueryWrapper<BizPurchaseRequest>()
                    .eq(BizPurchaseRequest::getRequestNo, request.getRequestNo().trim()));
            if (count > 0) throw new BusinessException("采购申请单号[" + request.getRequestNo() + "]已存在");
            request.setRequestNo(request.getRequestNo().trim());
        } else {
            request.setRequestNo(CodeUtil.generate("PR"));
        }
        request.setStatus("draft");
        // 自动填充物料名称/规格/单位
        if (request.getMaterialId() != null) {
            BizMaterial material = materialMapper.selectById(request.getMaterialId());
            if (material != null) {
                if (!StringUtils.hasText(request.getMaterialName())) request.setMaterialName(material.getMaterialName());
                if (!StringUtils.hasText(request.getSpec())) request.setSpec(material.getSpec());
                if (!StringUtils.hasText(request.getUnit())) request.setUnit(material.getUnit());
            }
        }
        save(request);
    }

    /** 编辑采购申请: 只允许 draft 状态修改, 修改字段限 quantity / expectedDate / suggestedSupplierId / estimatedPrice / remark */
    public void updateDraft(Long id, BizPurchaseRequest patch) {
        BizPurchaseRequest cur = getById(id);
        if (cur == null) throw new BusinessException("采购申请不存在");
        if (!"draft".equals(cur.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        if (patch.getQuantity() != null) cur.setQuantity(patch.getQuantity());
        if (patch.getExpectedDate() != null) cur.setExpectedDate(patch.getExpectedDate());
        if (patch.getSuggestedSupplierId() != null) cur.setSuggestedSupplierId(patch.getSuggestedSupplierId());
        if (patch.getEstimatedPrice() != null) cur.setEstimatedPrice(patch.getEstimatedPrice());
        if (patch.getRemark() != null) cur.setRemark(patch.getRemark());
        updateById(cur);
    }

    /** 提交草稿: draft → pending (或 approved, 取决于审批配置) */
    public void submitDraft(Long id) {
        BizPurchaseRequest cur = getById(id);
        if (cur == null) throw new BusinessException("采购申请不存在");
        if (!"draft".equals(cur.getStatus())) throw new BusinessException("仅草稿状态可提交");
        boolean needApproval = bizConfigService.getBoolValue("purchase.request.need_approval", true);
        cur.setStatus(needApproval ? "pending" : "approved");
        updateById(cur);
    }

    /**
     * 审批通过
     */
    public void approve(Long id, Long approvedBy, String approveRemark) {
        BizPurchaseRequest request = getById(id);
        if (request == null) {
            throw new BusinessException("采购申请不存在");
        }
        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException("该申请已审批");
        }
        request.setApprovedBy(approvedBy);
        request.setApprovedAt(LocalDateTime.now());
        request.setApproveRemark(approveRemark);
        request.setStatus("approved");
        updateById(request);
    }

    /**
     * 审批驳回
     */
    public void reject(Long id, Long approvedBy, String approveRemark) {
        BizPurchaseRequest request = getById(id);
        if (request == null) {
            throw new BusinessException("采购申请不存在");
        }
        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException("该申请已审批");
        }
        request.setApprovedBy(approvedBy);
        request.setApprovedAt(LocalDateTime.now());
        request.setApproveRemark(approveRemark);
        request.setStatus("rejected");
        updateById(request);
    }

    /**
     * 删除采购申请（仅草稿状态，物理删除）
     */
    public void delete(Long id) {
        BizPurchaseRequest request = getById(id);
        if (request == null) {
            throw new BusinessException("采购申请不存在");
        }
        if (!"pending".equals(request.getStatus()) && !"draft".equals(request.getStatus())) {
            throw new BusinessException("只有草稿/待审批状态的采购申请可以删除");
        }
        removeById(id);
    }

    /**
     * 下发采购订单（功能3）
     * 将一个或多个已审批的采购申请转为采购订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void issueToOrder(List<Long> requestIds, BizPurchaseOrder order, List<BizPurchaseOrderItem> items) {
        if (requestIds == null || requestIds.isEmpty()) {
            throw new BusinessException("请选择采购申请");
        }

        List<BizPurchaseRequest> requests = listByIds(requestIds);
        if (requests.size() != requestIds.size()) {
            throw new BusinessException("部分采购申请不存在");
        }
        for (BizPurchaseRequest r : requests) {
            if (!"approved".equals(r.getStatus())) {
                throw new BusinessException("采购申请[" + r.getRequestNo() + "]未审批通过，不能下发");
            }
        }

        // 检查是否已存在关联的有效采购订单（非驳回状态）
        List<BizPurchaseOrderItem> existingLinks = orderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .in(BizPurchaseOrderItem::getRequestId, requestIds));
        if (!existingLinks.isEmpty()) {
            Set<Long> linkedOrderIds = existingLinks.stream()
                    .map(BizPurchaseOrderItem::getPurchaseOrderId)
                    .collect(Collectors.toSet());
            boolean hasActiveOrder = orderService.listByIds(linkedOrderIds).stream()
                    .anyMatch(o -> !"rejected".equals(o.getStatus()));
            if (hasActiveOrder) {
                throw new BusinessException("选中的采购申请已存在关联的有效采购订单，无法重复下发");
            }
        }

        // 创建采购订单
        orderService.create(order, items);

        // 将申请状态更新为 issued
        update(new LambdaUpdateWrapper<BizPurchaseRequest>()
                .in(BizPurchaseRequest::getId, requestIds)
                .set(BizPurchaseRequest::getStatus, "issued"));
    }
}

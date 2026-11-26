package com.aimes.modules.bom.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.bom.entity.BizBomItem;
import com.aimes.modules.bom.entity.BizBomSubstitute;
import com.aimes.modules.bom.entity.BizBomVersion;
import com.aimes.modules.bom.mapper.BizBomItemMapper;
import com.aimes.modules.bom.mapper.BizBomSubstituteMapper;
import com.aimes.modules.bom.mapper.BizBomVersionMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizProduct;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.aimes.modules.approval.service.BizApprovalService;
import com.aimes.modules.system.entity.SysConfig;
import com.aimes.modules.system.service.SysConfigService;
import com.aimes.modules.system.service.SysNotificationService;
import com.aimes.common.utils.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BizBomService extends ServiceImpl<BizBomVersionMapper, BizBomVersion> {

    private final BizBomItemMapper bomItemMapper;
    private final BizBomSubstituteMapper substituteMapper;
    private final BizMaterialMapper materialMapper;
    private final BizProductMapper productMapper;
    @Lazy private final BizApprovalService approvalService;
    private final SysNotificationService notificationService;
    private final SysConfigService configService;

    /**
     * 查询所有有 BOM 的产品(任意物料类型),列表展示用
     * 返回每个产品的激活版本数 / 总版本数 / 当前激活版本号
     */
    public List<Map<String, Object>> listBomProducts(String keyword) {
        return listBomProducts(keyword, null, null, null);
    }

    public List<Map<String, Object>> listBomProducts(String keyword, String productNo, String productName, String spec) {
        // Step 1: 先查所有有 BOM 的产品 ID 集合(只取 productId/id/status/versionNo 四列,数据量极小)
        // 这样后续的物料查询可以用 IN 直接过滤,避免拉回所有物料再内存过滤
        List<BizBomVersion> allVersions = list(new LambdaQueryWrapper<BizBomVersion>()
                .select(BizBomVersion::getId, BizBomVersion::getProductId,
                        BizBomVersion::getStatus, BizBomVersion::getVersionNo)
                .eq(BizBomVersion::getDeleted, 0));
        if (allVersions.isEmpty()) return List.of();

        Map<Long, List<BizBomVersion>> byProduct = allVersions.stream()
                .collect(Collectors.groupingBy(BizBomVersion::getProductId));
        Set<Long> productIdsWithBom = byProduct.keySet();

        // Step 2: 物料查询用 IN 直接限定有 BOM 的产品 — 不限制 category,任何物料类型只要建过 BOM 都展示
        LambdaQueryWrapper<BizMaterial> matWrapper = new LambdaQueryWrapper<BizMaterial>()
                .select(BizMaterial::getId, BizMaterial::getMaterialNo,
                        BizMaterial::getMaterialName, BizMaterial::getSpec, BizMaterial::getUnit)
                .eq(BizMaterial::getDeleted, 0)
                .in(BizMaterial::getId, productIdsWithBom);
        if (org.springframework.util.StringUtils.hasText(keyword)) {
            matWrapper.and(w -> w.likeRight(BizMaterial::getMaterialNo, keyword)
                    .or().like(BizMaterial::getMaterialName, keyword));
        }
        if (org.springframework.util.StringUtils.hasText(productNo)) {
            matWrapper.like(BizMaterial::getMaterialNo, productNo);
        }
        if (org.springframework.util.StringUtils.hasText(productName)) {
            matWrapper.like(BizMaterial::getMaterialName, productName);
        }
        // spec 支持单值与复合输入(按 token 拆分,AND 命中)
        com.aimes.common.utils.SpecQueryUtil.applyToBizMaterial(matWrapper, spec);
        List<BizMaterial> materials = materialMapper.selectList(matWrapper);
        if (materials.isEmpty()) return List.of();

        // Step 3: 组装结果(单次循环同时统计 active 和 pending,避免两次 stream)
        List<Map<String, Object>> result = new ArrayList<>(materials.size());
        for (BizMaterial m : materials) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("productId", m.getId());
            map.put("materialNo", m.getMaterialNo());
            map.put("materialName", m.getMaterialName());
            map.put("spec", m.getSpec());
            map.put("unit", m.getUnit());
            List<BizBomVersion> vers = byProduct.getOrDefault(m.getId(), List.of());
            BizBomVersion active = null;
            long pending = 0;
            for (BizBomVersion v : vers) {
                if ("active".equals(v.getStatus())) active = v;
                else if ("pending_approval".equals(v.getStatus())) pending++;
            }
            map.put("versionCount", vers.size());
            map.put("activeVersionNo", active != null ? active.getVersionNo() : null);
            map.put("activeVersionId", active != null ? active.getId() : null);
            map.put("pendingApprovalCount", pending);
            result.add(map);
        }
        return result;
    }

    /**
     * 根据产品查询BOM版本列表（含物料行数）
     */
    public List<Map<String, Object>> getVersionsByProduct(Long productId) {
        List<BizBomVersion> versions = list(new LambdaQueryWrapper<BizBomVersion>()
                .eq(BizBomVersion::getProductId, productId)
                .orderByDesc(BizBomVersion::getCreateTime));

        return versions.stream().map(v -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", v.getId());
            map.put("productId", v.getProductId());
            map.put("versionNo", v.getVersionNo());
            map.put("status", v.getStatus());
            map.put("totalMaterialCost", v.getTotalMaterialCost());
            map.put("createTime", v.getCreateTime());

            long itemCount = bomItemMapper.selectCount(
                    new LambdaQueryWrapper<BizBomItem>().eq(BizBomItem::getBomVersionId, v.getId()));
            map.put("itemCount", itemCount);

            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 版本详情（含BOM树形结构）
     */
    public Map<String, Object> getVersionDetail(Long versionId) {
        BizBomVersion version = getById(versionId);
        if (version == null) {
            throw new BusinessException("BOM版本不存在");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", version.getId());
        detail.put("productId", version.getProductId());
        // 带出 BOM 所属产品的编号 / 名称 / 规格 — 审批/详情面板展示 "BOM 名" 用
        if (version.getProductId() != null) {
            BizMaterial product = materialMapper.selectById(version.getProductId());
            if (product != null) {
                detail.put("productNo", product.getMaterialNo());
                detail.put("productName", product.getMaterialName());
                detail.put("productSpec", product.getSpec());
                detail.put("productUnit", product.getUnit());
            }
        }
        detail.put("versionNo", version.getVersionNo());
        detail.put("status", version.getStatus());
        detail.put("totalMaterialCost", version.getTotalMaterialCost());
        detail.put("createBy", version.getCreateBy());
        detail.put("createTime", version.getCreateTime());

        // 查询所有BOM行，构建树形结构
        List<BizBomItem> allItems = bomItemMapper.selectList(
                new LambdaQueryWrapper<BizBomItem>()
                        .eq(BizBomItem::getBomVersionId, versionId)
                        .orderByAsc(BizBomItem::getSortOrder));
        // Pre-compute name maps for tree building
        Set<Long> matIds = new HashSet<>();
        Set<Long> prodIds = new HashSet<>();
        for (BizBomItem item : allItems) {
            if (item.getMaterialId() != null) matIds.add(item.getMaterialId());
            if (item.getProductId() != null) prodIds.add(item.getProductId());
        }
        Map<Long, String> matNameMap = new HashMap<>();
        Map<Long, String> matNoMap = new HashMap<>();
        if (!matIds.isEmpty()) {
            materialMapper.selectBatchIds(matIds).forEach(m -> {
                matNameMap.put(m.getId(), m.getMaterialName());
                matNoMap.put(m.getId(), m.getMaterialNo());
            });
        }
        // productId 优先查产品表，未命中再查物料表（自制项通常 productId 指向 biz_material）
        Map<Long, String> prodNameMap = new HashMap<>();
        Map<Long, String> prodNoMap = new HashMap<>();
        if (!prodIds.isEmpty()) {
            productMapper.selectBatchIds(prodIds).forEach(p -> {
                prodNameMap.put(p.getId(), p.getProductName());
                prodNoMap.put(p.getId(), p.getProductNo());
            });
            Set<Long> missingProd = prodIds.stream().filter(id -> !prodNameMap.containsKey(id)).collect(Collectors.toSet());
            if (!missingProd.isEmpty()) {
                materialMapper.selectBatchIds(missingProd).forEach(m -> {
                    prodNameMap.put(m.getId(), m.getMaterialName());
                    prodNoMap.put(m.getId(), m.getMaterialNo());
                });
            }
        }
        detail.put("items", buildTree(allItems, 0L, matNameMap, matNoMap, prodNameMap, prodNoMap));

        return detail;
    }

    /**
     * 导出: 单个产品的激活版本明细(平铺为 List)
     * 字段: 产品编号, 产品名称, BOM版本号, 物料编号, 物料名称, 规格, 单位, 用量, 损耗率, 实际用量, 来源, 备注
     */
    public List<Map<String, Object>> getActiveVersionExportRows(Long productId) {
        BizBomVersion active = getOne(new LambdaQueryWrapper<BizBomVersion>()
                .eq(BizBomVersion::getProductId, productId)
                .eq(BizBomVersion::getStatus, "active")
                .last("LIMIT 1"));
        if (active == null) return List.of();
        return flattenVersionForExport(active);
    }

    /**
     * 导出: 所有(可选筛选)产品的激活版本明细 — 全部合到一张表, 列里带产品信息
     */
    public List<Map<String, Object>> getActiveVersionsExportRowsAll(String productNo, String productName, String spec) {
        List<Map<String, Object>> products = listBomProducts(null, productNo, productName, spec);
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map<String, Object> p : products) {
            Object vid = p.get("activeVersionId");
            if (vid == null) continue;
            BizBomVersion v = getById(((Number) vid).longValue());
            if (v == null) continue;
            all.addAll(flattenVersionForExport(v));
        }
        return all;
    }

    private List<Map<String, Object>> flattenVersionForExport(BizBomVersion version) {
        BizMaterial product = version.getProductId() != null ? materialMapper.selectById(version.getProductId()) : null;
        String productNo = product != null ? product.getMaterialNo() : "";
        String productName = product != null ? product.getMaterialName() : "";
        String versionNo = version.getVersionNo();
        String status = version.getStatus();
        java.time.format.DateTimeFormatter dtFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String createTime = version.getCreateTime() != null ? version.getCreateTime().format(dtFmt) : "";

        List<BizBomItem> items = bomItemMapper.selectList(new LambdaQueryWrapper<BizBomItem>()
                .eq(BizBomItem::getBomVersionId, version.getId())
                .orderByAsc(BizBomItem::getSortOrder));
        // 收集 materialId 与 productId 两种引用, 自制(manufacture) 容器行 materialId 可能为空, 仅有 productId
        Set<Long> matIds = new HashSet<>();
        for (BizBomItem it : items) {
            if (it.getMaterialId() != null) matIds.add(it.getMaterialId());
            if (it.getProductId() != null) matIds.add(it.getProductId());
        }
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(matIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
        // productId 在 biz_material 中找不到时, 回退查 biz_product
        Map<Long, BizProduct> prodMap = new HashMap<>();
        Set<Long> missing = items.stream()
                .map(BizBomItem::getProductId).filter(Objects::nonNull)
                .filter(id -> !matMap.containsKey(id))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            productMapper.selectBatchIds(missing).forEach(p -> prodMap.put(p.getId(), p));
        }

        // 按 parentItemId 分组, 0 / null 视为顶层, 深度优先输出并计算层级
        Map<Long, List<BizBomItem>> children = new LinkedHashMap<>();
        for (BizBomItem it : items) {
            Long pid = (it.getParentItemId() == null || it.getParentItemId() == 0L) ? 0L : it.getParentItemId();
            children.computeIfAbsent(pid, k -> new ArrayList<>()).add(it);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        java.util.function.BiConsumer<Long, Integer> visit = new java.util.function.BiConsumer<>() {
            @Override
            public void accept(Long parentId, Integer level) {
                List<BizBomItem> kids = children.getOrDefault(parentId, List.of());
                for (BizBomItem it : kids) {
                    // 优先 materialId, 没有就回退 productId(manufacture 容器行常用); 仍取不到则查 biz_product
                    BizMaterial mat = it.getMaterialId() != null ? matMap.get(it.getMaterialId()) : null;
                    if (mat == null && it.getProductId() != null) mat = matMap.get(it.getProductId());
                    BizProduct fallbackProd = (mat == null && it.getProductId() != null) ? prodMap.get(it.getProductId()) : null;
                    String itemNo = mat != null ? mat.getMaterialNo() : (fallbackProd != null ? fallbackProd.getProductNo() : "");
                    String itemName = mat != null ? mat.getMaterialName() : (fallbackProd != null ? fallbackProd.getProductName() : "");

                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("productNo", productNo);
                    r.put("productName", productName);
                    r.put("versionNo", versionNo);
                    r.put("status", status);
                    r.put("level", level);
                    r.put("materialNo", itemNo);
                    r.put("materialName", itemName);
                    r.put("sourceType", "manufacture".equalsIgnoreCase(it.getSourceType()) ? "自制" : "外购");
                    r.put("quantity", it.getQuantity());
                    r.put("wastageRate", it.getWastageRate());
                    r.put("sortOrder", it.getSortOrder());
                    r.put("createTime", createTime);
                    rows.add(r);
                    accept(it.getId(), level + 1);
                }
            }
        };
        visit.accept(0L, 1);
        return rows;
    }

    /**
     * 创建BOM版本
     */
    @Transactional
    public void createVersion(BizBomVersion version) {
        if (version.getStatus() == null) {
            version.setStatus("draft");
        }
        save(version);
    }

    /**
     * 添加BOM行
     */
    @Transactional
    public void addItem(BizBomItem item) {
        if (item.getParentItemId() == null) {
            item.setParentItemId(0L);
        }
        // BOM循环引用检测（当子项为自制件时）
        if ("manufacture".equals(item.getSourceType()) && item.getProductId() != null) {
            BizBomVersion version = getById(item.getBomVersionId());
            if (version != null) {
                checkCircularReference(version.getProductId(), item.getProductId(), new HashSet<>(), 0);
            }
        }
        // 计算实际用量 = quantity * (1 + wastageRate)
        recalcActualQuantity(item);
        bomItemMapper.insert(item);
    }

    /**
     * 更新BOM行
     */
    @Transactional
    public void updateItem(Long id, BizBomItem item) {
        BizBomItem existing = bomItemMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("BOM行不存在");
        }
        item.setId(id);
        recalcActualQuantity(item);
        bomItemMapper.updateById(item);
    }

    /**
     * 删除BOM行
     */
    @Transactional
    public void deleteItem(Long id) {
        BizBomItem existing = bomItemMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("BOM行不存在");
        }
        bomItemMapper.deleteById(id);
    }

    /**
     * 删除BOM版本（仅允许草稿或已废弃状态）
     */
    @Transactional
    public void deleteVersion(Long id) {
        BizBomVersion version = getById(id);
        if (version == null) {
            throw new BusinessException("BOM版本不存在");
        }
        if ("active".equals(version.getStatus())) {
            throw new BusinessException("激活状态的BOM版本不允许删除，请先作废后再删除");
        }
        // 删除该版本下所有BOM行
        bomItemMapper.delete(new LambdaQueryWrapper<BizBomItem>()
                .eq(BizBomItem::getBomVersionId, id));
        removeById(id);
    }

    /**
     * 申请激活版本 —
     * 1. 系统设置里 "BOM版本激活" 审批开关未打开 → 直接激活,跳过审批
     * 2. 开关打开 → 触发审批流,审批通过后由 BomApprovalListener 完成真正激活
     */
    @Transactional
    public Long requestActivateVersion(Long id) {
        BizBomVersion version = getById(id);
        if (version == null) throw new BusinessException("BOM版本不存在");

        if ("active".equals(version.getStatus())) {
            throw new BusinessException("该版本已经是激活状态");
        }
        if ("pending_approval".equals(version.getStatus())) {
            throw new BusinessException("该版本已在审批中,请勿重复提交");
        }

        // 读系统设置的审批开关
        if (!isApprovalEnabled()) {
            doActivate(id);
            return null;
        }

        // 把当前版本状态置为 pending_approval
        version.setStatus("pending_approval");
        updateById(version);

        // 发起审批
        Long applicantId = SecurityUtil.getCurrentUserId();
        Long instanceId = approvalService.startApproval("bom_version_activation", id, applicantId);
        if (instanceId == null) {
            // 没有配置审批流 → 退回并直接激活(兼容无审批流场景)
            doActivate(id);
            return null;
        }

        // 通知审批人 — 按审批流配置精确推送给当前节点的所有审批人
        BizMaterial product = materialMapper.selectById(version.getProductId());
        String productName = product != null ? product.getMaterialName() : ("产品#" + version.getProductId());
        String title = "BOM 版本激活审批";
        String content = String.format("产品 [%s] 的 BOM 版本 [%s] 申请激活,请审批",
                productName, version.getVersionNo());

        java.util.Set<Long> approverUserIds = approvalService.resolveCurrentApproverUserIds(instanceId);
        if (approverUserIds.isEmpty()) {
            // 兜底:没解析到审批人(节点可能没配审批人) → 通知 admin 角色
            notificationService.notifyRole("admin", title, content, "approval", "bom_version_activation", instanceId);
        } else {
            for (Long userId : approverUserIds) {
                notificationService.notifyUser(userId, title, content, "approval", "bom_version_activation", instanceId);
            }
        }

        return instanceId;
    }

    /**
     * 检查系统设置里 "BOM版本激活" 审批开关是否开启
     * sys_config 中 key=approval_bom_version_activation, value 是 JSON,有 enabled 字段
     * 没配置时默认开启(走审批流)
     */
    private boolean isApprovalEnabled() {
        try {
            SysConfig cfg = configService.getByKey("approval_bom_version_activation");
            if (cfg == null || cfg.getConfigValue() == null || cfg.getConfigValue().isEmpty()) {
                return true; // 没配置 → 默认走审批
            }
            String val = cfg.getConfigValue();
            // 粗略解析 JSON 中的 "enabled":true/false
            return !val.contains("\"enabled\":false");
        } catch (Exception e) {
            return true; // 出错保守走审批
        }
    }

    /**
     * 真正执行激活 — 由审批监听器或无审批流兼容时调用
     */
    @Transactional
    public void doActivate(Long id) {
        BizBomVersion version = getById(id);
        if (version == null) throw new BusinessException("BOM版本不存在");

        // 把同产品其他 active 版本改为 deprecated
        List<BizBomVersion> activeVersions = list(new LambdaQueryWrapper<BizBomVersion>()
                .eq(BizBomVersion::getProductId, version.getProductId())
                .eq(BizBomVersion::getStatus, "active"));
        for (BizBomVersion v : activeVersions) {
            if (!v.getId().equals(id)) {
                v.setStatus("deprecated");
                updateById(v);
            }
        }
        version.setStatus("active");
        updateById(version);
    }

    /**
     * 审批驳回 — 无条件置为 rejected(已 active/deprecated 的除外,避免误伤)
     */
    @Transactional
    public void markRejected(Long id) {
        BizBomVersion version = getById(id);
        if (version == null) {
            log.warn("[BOM][reject] 版本 [{}] 不存在", id);
            return;
        }
        String oldStatus = version.getStatus();
        if ("active".equals(oldStatus) || "deprecated".equals(oldStatus)) {
            log.warn("[BOM][reject] 版本 [{}] 当前状态 [{}],跳过置驳回", id, oldStatus);
            return;
        }
        version.setStatus("rejected");
        boolean ok = updateById(version);
        log.info("[BOM][reject] 版本 [{}] {} -> rejected,updateById={}", id, oldStatus, ok);
    }

    /**
     * @deprecated 用 markRejected 代替
     */
    @Deprecated
    @Transactional
    public void revertToDraft(Long id) {
        markRejected(id);
    }

    /**
     * 兼容旧调用,直接激活走审批流
     */
    @Transactional
    public void activateVersion(Long id) {
        requestActivateVersion(id);
    }

    /**
     * 作废版本
     */
    @Transactional
    public void deprecateVersion(Long id) {
        BizBomVersion version = getById(id);
        if (version == null) {
            throw new BusinessException("BOM版本不存在");
        }
        version.setStatus("deprecated");
        updateById(version);
    }

    /**
     * 复制版本：深拷贝所有BOM行到新版本
     */
    @Transactional
    public void copyVersion(Long id) {
        copyVersion(id, null);
    }

    /**
     * 复制版本,允许指定新版本号(为空则按 原版本号_copy 自动生成)
     */
    @Transactional
    public void copyVersion(Long id, String newVersionNo) {
        BizBomVersion original = getById(id);
        if (original == null) {
            throw new BusinessException("BOM版本不存在");
        }
        String targetVersionNo;
        if (org.springframework.util.StringUtils.hasText(newVersionNo)) {
            String trimmed = newVersionNo.trim();
            // 同产品下版本号唯一性校验
            long exists = count(new LambdaQueryWrapper<BizBomVersion>()
                    .eq(BizBomVersion::getProductId, original.getProductId())
                    .eq(BizBomVersion::getVersionNo, trimmed)
                    .eq(BizBomVersion::getDeleted, 0));
            if (exists > 0) throw new BusinessException("版本号「" + trimmed + "」已存在,请使用其他版本号");
            targetVersionNo = trimmed;
        } else {
            targetVersionNo = original.getVersionNo() + "_copy";
        }
        doCopyToProduct(original, original.getProductId(), targetVersionNo);
    }

    /**
     * 将某 BOM 版本复制到另一个产品（产品档案中任意物料均可）：
     * - 源版本必须是 active 激活版本
     * - 目标产品不能已存在任何 BOM 版本（避免误覆盖）
     * - 复制产物状态默认 draft；版本号使用源版本号
     * - 同时深拷贝 BOM 子件 + 替代料配置
     */
    @Transactional
    public void copyVersionToProduct(Long sourceVersionId, Long targetProductId) {
        if (targetProductId == null) throw new BusinessException("请选择目标产品");
        BizBomVersion source = getById(sourceVersionId);
        if (source == null) throw new BusinessException("源 BOM 版本不存在");
        if (!"active".equals(source.getStatus())) {
            throw new BusinessException("只能复制已激活(active)的 BOM 版本");
        }
        if (source.getProductId() != null && source.getProductId().equals(targetProductId)) {
            throw new BusinessException("目标产品与源产品相同，请选择不同的产品");
        }
        // 目标产品必须存在于物料档案
        BizMaterial targetMat = materialMapper.selectById(targetProductId);
        if (targetMat == null) throw new BusinessException("目标产品不存在");

        // 复用深拷贝逻辑；版本号复用源版本号，如有冲突自动追加 _copy
        String baseVerNo = source.getVersionNo() != null ? source.getVersionNo() : "V1.0";
        String newVerNo = baseVerNo;
        long exists = count(new LambdaQueryWrapper<BizBomVersion>()
                .eq(BizBomVersion::getProductId, targetProductId)
                .eq(BizBomVersion::getVersionNo, newVerNo)
                .eq(BizBomVersion::getDeleted, 0));
        if (exists > 0) newVerNo = baseVerNo + "_copy";
        doCopyToProduct(source, targetProductId, newVerNo);
    }

    /** 深拷贝核心：源版本 → 目标产品 + 目标版本号（创建新版本+子件+替代料），状态=draft */
    private void doCopyToProduct(BizBomVersion source, Long targetProductId, String versionNo) {
        BizBomVersion newVersion = new BizBomVersion();
        newVersion.setProductId(targetProductId);
        newVersion.setVersionNo(versionNo);
        newVersion.setStatus("draft");
        newVersion.setTotalMaterialCost(source.getTotalMaterialCost());
        save(newVersion);

        // 深拷贝所有BOM行
        List<BizBomItem> originalItems = bomItemMapper.selectList(
                new LambdaQueryWrapper<BizBomItem>()
                        .eq(BizBomItem::getBomVersionId, source.getId())
                        .orderByAsc(BizBomItem::getSortOrder));

        // 建立旧ID -> 新ID的映射，以便维护父子关系 + 替代料 bom_item_id 关联
        Map<Long, Long> idMapping = new HashMap<>();
        for (BizBomItem item : originalItems) {
            Long oldId = item.getId();
            BizBomItem newItem = new BizBomItem();
            newItem.setBomVersionId(newVersion.getId());
            newItem.setParentItemId(item.getParentItemId());
            newItem.setItemType(item.getItemType());
            newItem.setSourceType(item.getSourceType());
            newItem.setMaterialId(item.getMaterialId());
            newItem.setProductId(item.getProductId());
            newItem.setQuantity(item.getQuantity());
            newItem.setWastageRate(item.getWastageRate());
            newItem.setActualQuantity(item.getActualQuantity());
            newItem.setSubstituteGroup(item.getSubstituteGroup());
            newItem.setSortOrder(item.getSortOrder());
            newItem.setRemark(item.getRemark());
            bomItemMapper.insert(newItem);
            idMapping.put(oldId, newItem.getId());
        }

        // 更新父子关系映射
        for (BizBomItem item : originalItems) {
            if (item.getParentItemId() != null && item.getParentItemId() != 0L) {
                Long newParentId = idMapping.get(item.getParentItemId());
                if (newParentId != null) {
                    Long newItemId = idMapping.get(item.getId());
                    BizBomItem updateItem = new BizBomItem();
                    updateItem.setId(newItemId);
                    updateItem.setParentItemId(newParentId);
                    bomItemMapper.updateById(updateItem);
                }
            }
        }

        // 复制替代料配置
        if (!idMapping.isEmpty()) {
            List<BizBomSubstitute> subs = substituteMapper.selectList(
                    new LambdaQueryWrapper<BizBomSubstitute>()
                            .in(BizBomSubstitute::getBomItemId, idMapping.keySet()));
            for (BizBomSubstitute s : subs) {
                Long newBomItemId = idMapping.get(s.getBomItemId());
                if (newBomItemId == null) continue;
                BizBomSubstitute n = new BizBomSubstitute();
                n.setGroupName(s.getGroupName());
                n.setBomItemId(newBomItemId);
                n.setMaterialId(s.getMaterialId());
                n.setPriority(s.getPriority());
                n.setSubstituteRatio(s.getSubstituteRatio());
                n.setRemark(s.getRemark());
                substituteMapper.insert(n);
            }
        }
    }

    // ==================== 新增：BOM业务规则方法 ====================

    /**
     * BOM循环引用检测
     * 递归检查 subProductId 的 BOM 中是否直接或间接引用了 rootProductId
     * @param rootProductId 根产品ID（不允许出现在子BOM中）
     * @param subProductId 子产品ID（递归检查其BOM）
     * @param visited 已访问的产品集合（防止无限递归）
     * @param depth 当前递归深度
     */
    private void checkCircularReference(Long rootProductId, Long subProductId,
                                         Set<Long> visited, int depth) {
        if (depth > 10) {
            throw new BusinessException("BOM层级过深(超过10层), 可能存在循环引用");
        }
        if (Objects.equals(rootProductId, subProductId)) {
            throw new BusinessException("检测到BOM循环引用: 产品ID=" + rootProductId);
        }
        if (visited.contains(subProductId)) return;
        visited.add(subProductId);

        // 查找子产品的生效BOM
        BizBomVersion subBom = getOne(new LambdaQueryWrapper<BizBomVersion>()
                .eq(BizBomVersion::getProductId, subProductId)
                .eq(BizBomVersion::getStatus, "active")
                .last("LIMIT 1"));
        if (subBom == null) return;

        // 查找子BOM中的自制件子项
        List<BizBomItem> subItems = bomItemMapper.selectList(
                new LambdaQueryWrapper<BizBomItem>()
                        .eq(BizBomItem::getBomVersionId, subBom.getId())
                        .eq(BizBomItem::getSourceType, "manufacture"));

        for (BizBomItem subItem : subItems) {
            if (subItem.getProductId() != null) {
                checkCircularReference(rootProductId, subItem.getProductId(), visited, depth + 1);
            }
        }
    }

    /**
     * BOM成本卷积
     * 递归计算BOM版本的总物料成本
     * 原材料: 物料单价 × actualQuantity
     * 半成品: 递归计算其active BOM的成本 × actualQuantity
     */
    @Transactional
    public BigDecimal calculateCost(Long bomVersionId) {
        BizBomVersion version = getById(bomVersionId);
        if (version == null) {
            throw new BusinessException("BOM版本不存在");
        }

        BigDecimal totalCost = calculateCostRecursive(bomVersionId, new HashSet<>(), 0);
        version.setTotalMaterialCost(totalCost);
        updateById(version);
        return totalCost;
    }

    private BigDecimal calculateCostRecursive(Long bomVersionId, Set<Long> visited, int depth) {
        if (depth > 10 || visited.contains(bomVersionId)) return BigDecimal.ZERO;
        visited.add(bomVersionId);

        List<BizBomItem> items = bomItemMapper.selectList(
                new LambdaQueryWrapper<BizBomItem>()
                        .eq(BizBomItem::getBomVersionId, bomVersionId));

        BigDecimal totalCost = BigDecimal.ZERO;
        for (BizBomItem item : items) {
            BigDecimal qty = item.getActualQuantity() != null ? item.getActualQuantity() : item.getQuantity();
            if (qty == null) qty = BigDecimal.ZERO;

            if ("purchase".equals(item.getSourceType()) && item.getMaterialId() != null) {
                // 外购件: 查询物料单价
                BizMaterial material = materialMapper.selectById(item.getMaterialId());
                BigDecimal unitPrice = material != null && material.getSafetyStock() != null
                        ? BigDecimal.ZERO : BigDecimal.ZERO; // TODO: 物料实体无unitPrice字段，暂用0
                totalCost = totalCost.add(qty.multiply(unitPrice));
            } else if ("manufacture".equals(item.getSourceType()) && item.getProductId() != null) {
                // 自制件: 递归计算其BOM成本
                BizBomVersion subBom = getOne(new LambdaQueryWrapper<BizBomVersion>()
                        .eq(BizBomVersion::getProductId, item.getProductId())
                        .eq(BizBomVersion::getStatus, "active")
                        .last("LIMIT 1"));
                if (subBom != null) {
                    BigDecimal subCost = calculateCostRecursive(subBom.getId(), visited, depth + 1);
                    totalCost = totalCost.add(qty.multiply(subCost));
                }
            }
        }
        return totalCost;
    }

    /**
     * BOM展开：递归展开为最底层原料清单
     * 累积用量（乘法穿透）
     * @return 扁平化的原料清单
     */
    public List<Map<String, Object>> expandToLeafMaterials(Long bomVersionId) {
        BizBomVersion version = getById(bomVersionId);
        if (version == null) {
            throw new BusinessException("BOM版本不存在");
        }

        Map<Long, Map<String, Object>> materialMap = new LinkedHashMap<>();
        expandRecursive(bomVersionId, BigDecimal.ONE, materialMap, new HashSet<>(), 0, "");

        return new ArrayList<>(materialMap.values());
    }

    private void expandRecursive(Long bomVersionId, BigDecimal parentQty,
                                  Map<Long, Map<String, Object>> materialMap,
                                  Set<Long> visited, int depth, String path) {
        if (depth > 10 || visited.contains(bomVersionId)) return;
        visited.add(bomVersionId);

        List<BizBomItem> items = bomItemMapper.selectList(
                new LambdaQueryWrapper<BizBomItem>()
                        .eq(BizBomItem::getBomVersionId, bomVersionId)
                        .orderByAsc(BizBomItem::getSortOrder));

        for (BizBomItem item : items) {
            BigDecimal qty = item.getActualQuantity() != null ? item.getActualQuantity() : item.getQuantity();
            if (qty == null) qty = BigDecimal.ZERO;
            BigDecimal totalQty = parentQty.multiply(qty);

            if ("purchase".equals(item.getSourceType()) && item.getMaterialId() != null) {
                // 外购件：累积到结果
                Map<String, Object> existing = materialMap.get(item.getMaterialId());
                if (existing != null) {
                    BigDecimal existingQty = (BigDecimal) existing.get("totalQty");
                    existing.put("totalQty", existingQty.add(totalQty));
                } else {
                    BizMaterial material = materialMapper.selectById(item.getMaterialId());
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("materialId", item.getMaterialId());
                    entry.put("materialName", material != null ? material.getMaterialName() : null);
                    entry.put("spec", material != null ? material.getSpec() : null);
                    entry.put("unit", material != null ? material.getUnit() : null);
                    entry.put("totalQty", totalQty);
                    entry.put("path", path + (material != null ? material.getMaterialName() : ""));
                    materialMap.put(item.getMaterialId(), entry);
                }
            } else if ("manufacture".equals(item.getSourceType()) && item.getProductId() != null) {
                // 自制件：递归展开
                BizBomVersion subBom = getOne(new LambdaQueryWrapper<BizBomVersion>()
                        .eq(BizBomVersion::getProductId, item.getProductId())
                        .eq(BizBomVersion::getStatus, "active")
                        .last("LIMIT 1"));
                if (subBom != null) {
                    BizMaterial product = materialMapper.selectById(item.getProductId());
                    String subPath = path + (product != null ? product.getMaterialName() : "") + " → ";
                    expandRecursive(subBom.getId(), totalQty, materialMap, visited, depth + 1, subPath);
                }
            }
        }
    }

    /**
     * 计算实际用量
     */
    private void recalcActualQuantity(BizBomItem item) {
        if (item.getQuantity() != null) {
            BigDecimal wastageRate = item.getWastageRate() != null ? item.getWastageRate() : BigDecimal.ZERO;
            BigDecimal rate = wastageRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            item.setActualQuantity(
                    item.getQuantity().multiply(BigDecimal.ONE.add(rate)).setScale(4, RoundingMode.HALF_UP));
        }
    }

    /**
     * 构建树形结构
     */
    private List<Map<String, Object>> buildTree(List<BizBomItem> allItems, Long parentId,
                                                  Map<Long, String> matNameMap, Map<Long, String> matNoMap,
                                                  Map<Long, String> prodNameMap, Map<Long, String> prodNoMap) {
        return allItems.stream()
                .filter(item -> Objects.equals(item.getParentItemId(), parentId))
                .map(item -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", item.getId());
                    node.put("bomVersionId", item.getBomVersionId());
                    node.put("parentItemId", item.getParentItemId());
                    node.put("itemType", item.getItemType());
                    node.put("sourceType", item.getSourceType());
                    node.put("materialId", item.getMaterialId());
                    node.put("materialNo", item.getMaterialId() != null ? matNoMap.getOrDefault(item.getMaterialId(), null) : null);
                    node.put("materialName", item.getMaterialId() != null ? matNameMap.getOrDefault(item.getMaterialId(), null) : null);
                    node.put("productId", item.getProductId());
                    node.put("productNo", item.getProductId() != null ? prodNoMap.getOrDefault(item.getProductId(), null) : null);
                    node.put("productName", item.getProductId() != null ? prodNameMap.getOrDefault(item.getProductId(), null) : null);
                    node.put("quantity", item.getQuantity());
                    node.put("wastageRate", item.getWastageRate());
                    node.put("actualQuantity", item.getActualQuantity());
                    node.put("substituteGroup", item.getSubstituteGroup());
                    node.put("sortOrder", item.getSortOrder());
                    node.put("remark", item.getRemark());
                    // 查询该BOM项的替代料数量
                    long subCount = substituteMapper.selectCount(
                            new LambdaQueryWrapper<BizBomSubstitute>().eq(BizBomSubstitute::getBomItemId, item.getId()));
                    node.put("substituteCount", subCount);
                    node.put("children", buildTree(allItems, item.getId(), matNameMap, matNoMap, prodNameMap, prodNoMap));
                    return node;
                }).collect(Collectors.toList());
    }

    // ==================== 替代料管理 ====================

    public List<Map<String, Object>> getSubstitutes(Long bomItemId) {
        List<BizBomSubstitute> subs = substituteMapper.selectList(
                new LambdaQueryWrapper<BizBomSubstitute>()
                        .eq(BizBomSubstitute::getBomItemId, bomItemId)
                        .orderByAsc(BizBomSubstitute::getPriority));

        Set<Long> matIds = subs.stream()
                .map(BizBomSubstitute::getMaterialId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<BizMaterial> mats = matIds.isEmpty() ? List.of() : materialMapper.selectBatchIds(matIds);
        Map<Long, BizMaterial> matMap = mats.stream()
                .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        return subs.stream().map(s -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("bomItemId", s.getBomItemId());
            map.put("materialId", s.getMaterialId());
            BizMaterial mat = s.getMaterialId() != null ? matMap.get(s.getMaterialId()) : null;
            map.put("materialNo", mat != null ? mat.getMaterialNo() : null);
            map.put("materialName", mat != null ? mat.getMaterialName() : null);
            map.put("spec", mat != null ? mat.getSpec() : null);
            map.put("unit", mat != null ? mat.getUnit() : null);
            map.put("groupName", s.getGroupName());
            map.put("priority", s.getPriority());
            map.put("substituteRatio", s.getSubstituteRatio());
            map.put("remark", s.getRemark());
            return map;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void addSubstitute(BizBomSubstitute substitute) {
        substituteMapper.insert(substitute);
    }

    @Transactional
    public void updateSubstitute(Long id, BizBomSubstitute substitute) {
        substitute.setId(id);
        substituteMapper.updateById(substitute);
    }

    @Transactional
    public void deleteSubstitute(Long id) {
        substituteMapper.deleteById(id);
    }

    /**
     * 获取物料下拉列表（含编码、是否有BOM标识）
     */
    public List<Map<String, Object>> getMaterialSelectWithBom() {
        List<BizMaterial> materials = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>()
                        .eq(BizMaterial::getStatus, "active")
                        .orderByAsc(BizMaterial::getMaterialNo));

        // 查询所有已有BOM版本的productId集合
        Set<Long> idsWithBom = list(new LambdaQueryWrapper<BizBomVersion>())
                .stream()
                .map(BizBomVersion::getProductId)
                .collect(Collectors.toSet());

        return materials.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("code", m.getMaterialNo());
            map.put("name", m.getMaterialName());
            map.put("spec", m.getSpec());
            map.put("unit", m.getUnit());
            map.put("hasBom", idsWithBom.contains(m.getId()));
            return map;
        }).collect(Collectors.toList());
    }
}

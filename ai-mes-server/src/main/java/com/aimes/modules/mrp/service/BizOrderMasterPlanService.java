package com.aimes.modules.mrp.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.bom.entity.BizBomItem;
import com.aimes.modules.bom.entity.BizBomSubstitute;
import com.aimes.modules.bom.entity.BizBomVersion;
import com.aimes.modules.bom.mapper.BizBomItemMapper;
import com.aimes.modules.bom.mapper.BizBomSubstituteMapper;
import com.aimes.modules.bom.mapper.BizBomVersionMapper;
import com.aimes.modules.inventory.entity.BizInventory;
import com.aimes.modules.inventory.mapper.BizInventoryMapper;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.master.entity.BizCustomer;
import com.aimes.modules.master.mapper.BizCustomerMapper;
import com.aimes.modules.mrp.entity.BizProductionPlan;
import com.aimes.modules.mrp.entity.BizProductionPlanItem;
import com.aimes.modules.mrp.entity.BizProductionPlanOrderRel;
import com.aimes.modules.mrp.mapper.BizProductionPlanItemMapper;
import com.aimes.modules.mrp.mapper.BizProductionPlanMapper;
import com.aimes.modules.mrp.mapper.BizProductionPlanOrderRelMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import org.springframework.transaction.annotation.Transactional;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单主计划: 根据销售订单递归展开 BOM, 每级算需求/库存/在途/缺口/齐套状态.
 * 纯实时计算, 不落表; 结果用于前端展示和触发"转采购单/转生产计划".
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BizOrderMasterPlanService {

    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final BizMaterialMapper materialMapper;
    private final BizCustomerMapper customerMapper;
    private final BizBomVersionMapper bomVersionMapper;
    private final BizBomItemMapper bomItemMapper;
    private final BizBomSubstituteMapper bizBomSubstituteMapper;
    private final BizInventoryMapper inventoryMapper;
    private final InventoryService inventoryService;
    private final BizPurchaseOrderMapper purchaseOrderMapper;
    private final BizPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final com.aimes.modules.purchase.service.PurchaseRequestService purchaseRequestService;
    private final com.aimes.modules.inventory.mapper.BizInvTaskMapper invTaskMapper;
    private final com.aimes.modules.inventory.mapper.BizInvTaskItemMapper invTaskItemMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizWoProcessTaskMapper woProcessTaskMapper;
    private final BizProductionPlanMapper productionPlanMapper;
    private final BizProductionPlanItemMapper productionPlanItemMapper;
    private final BizProductionPlanOrderRelMapper planOrderRelMapper;

    /** 把销售订单加入主计划 */
    public void addOrder(Long orderId) {
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        if (order.getMasterPlanAdded() != null && order.getMasterPlanAdded() == 1) {
            throw new BusinessException("该订单已加入主计划");
        }
        order.setMasterPlanAdded(1);
        order.setMasterPlanAddedAt(LocalDateTime.now());
        salesOrderMapper.updateById(order);
    }

    /** 从主计划移除 */
    public void removeOrder(Long orderId) {
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        order.setMasterPlanAdded(0);
        order.setMasterPlanAddedAt(null);
        salesOrderMapper.updateById(order);
    }

    /** 主计划订单列表: 已加入主计划的订单 + 客户名 (全部发完的自动剔除) */
    public List<Map<String, Object>> listOrders() {
        List<BizSalesOrder> orders = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>()
                        .eq(BizSalesOrder::getMasterPlanAdded, 1)
                        .eq(BizSalesOrder::getDeleted, 0)
                        .orderByDesc(BizSalesOrder::getMasterPlanAddedAt));
        if (orders.isEmpty()) return List.of();

        // 兜底: 历史路径可能直接改了 shippedQty 没走 updateOrderShipmentStatus,
        // 这里再扫一遍 — 全部发完的从主计划自动剔除并跳过
        List<BizSalesOrder> active = new ArrayList<>();
        for (BizSalesOrder o : orders) {
            if (isFullyShipped(o.getId())) {
                o.setMasterPlanAdded(0);
                o.setMasterPlanAddedAt(null);
                salesOrderMapper.updateById(o);
                continue;
            }
            active.add(o);
        }
        if (active.isEmpty()) return List.of();

        Set<Long> custIds = active.stream().map(BizSalesOrder::getCustomerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> custNameMap = custIds.isEmpty() ? Map.of()
                : customerMapper.selectBatchIds(custIds).stream()
                        .collect(Collectors.toMap(BizCustomer::getId, BizCustomer::getCustomerName, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizSalesOrder o : active) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("customerId", o.getCustomerId());
            m.put("customerName", o.getCustomerId() != null ? custNameMap.getOrDefault(o.getCustomerId(), "") : "");
            m.put("totalAmount", o.getTotalAmount());
            m.put("deliveryDate", o.getDeliveryDate());
            m.put("status", o.getStatus());
            m.put("masterPlanAddedAt", o.getMasterPlanAddedAt());
            result.add(m);
        }
        return result;
    }

    /** 订单的所有行项是否都已全部发货 (空订单视为未发完, 不剔除) */
    private boolean isFullyShipped(Long orderId) {
        List<BizSalesOrderItem> items = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, orderId));
        if (items.isEmpty()) return false;
        for (BizSalesOrderItem it : items) {
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal shipped = it.getShippedQty() != null ? it.getShippedQty() : BigDecimal.ZERO;
            if (shipped.compareTo(qty) < 0) return false;
        }
        return true;
    }

    /** 计算订单主计划完整树 (返回树形 List, 每条订单行项一棵树) */
    public List<Map<String, Object>> calculate(Long orderId) {
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("订单不存在");

        List<BizSalesOrderItem> items = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, orderId));
        if (items.isEmpty()) return List.of();

        // 一次性预加载本次涉及到的所有物料 — 减少递归里 N 次 selectById
        Map<Long, BizMaterial> materialCache = new HashMap<>();
        // 设置当前计算的订单 id, 让 getTotalAvailableInventory 扣"别的订单"的待发货承诺时排除自己
        currentCalcOrderId.set(orderId);
        committedDemandCache.remove();
        try {
            List<Map<String, Object>> roots = new ArrayList<>();
            for (BizSalesOrderItem item : items) {
                if (item.getProductId() == null) continue;
                // 销售订单要扣已发货的, 实际还需要做的 = 数量 - 已发货
                BigDecimal shipped = item.getShippedQty() != null ? item.getShippedQty() : BigDecimal.ZERO;
                BigDecimal needed = (item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO).subtract(shipped);
                if (needed.compareTo(BigDecimal.ZERO) <= 0) continue;
                roots.add(buildNode(item.getProductId(), needed, 1, materialCache, new HashSet<>(), null, null, null));
            }
            return roots;
        } finally {
            currentCalcOrderId.remove();
            committedDemandCache.remove();
            totalInventoryCache.remove();
            productionInTransitCache.remove();
            purchaseInTransitCache.remove();
            bomItemsCache.remove();
        }
    }

    /** 当前正在计算的订单 id (用于扣"别的订单"承诺时排除自己) */
    private final ThreadLocal<Long> currentCalcOrderId = new ThreadLocal<>();
    /** 单次计算内, 物料 → 其他订单未派发承诺量 的缓存 */
    private final ThreadLocal<Map<Long, BigDecimal>> committedDemandCache = ThreadLocal.withInitial(HashMap::new);
    /** 单次计算内, 物料 → 可用库存(扣三层) 的缓存. 同 BOM 多处出现的物料只查一次 */
    private final ThreadLocal<Map<Long, BigDecimal>> totalInventoryCache = ThreadLocal.withInitial(HashMap::new);
    /** 单次计算内, 物料 → 生产在途(本订单) 的缓存 */
    private final ThreadLocal<Map<Long, BigDecimal>> productionInTransitCache = ThreadLocal.withInitial(HashMap::new);
    /** 单次计算内, 物料 → 采购在途 的缓存 */
    private final ThreadLocal<Map<Long, BigDecimal>> purchaseInTransitCache = ThreadLocal.withInitial(HashMap::new);

    /**
     * 按 BOM 版本 ID 一次性加载该版本所有 BOM item, 单次 calculate() 内复用避免重复查询。
     * 必须用 ThreadLocal — 之前用实例字段时, Spring 单例下 cache 永不清空, 改了 BOM 也用旧数据。
     * calculate() 的 finally 里负责清掉, 保证下次进来重查 DB。
     */
    private final ThreadLocal<Map<Long, List<BizBomItem>>> bomItemsCache =
            ThreadLocal.withInitial(HashMap::new);
    private List<BizBomItem> getBomItemsByVersion(Long bomVersionId) {
        return bomItemsCache.get().computeIfAbsent(bomVersionId, vid ->
                bomItemMapper.selectList(
                        new LambdaQueryWrapper<BizBomItem>()
                                .eq(BizBomItem::getBomVersionId, vid)
                                .eq(BizBomItem::getDeleted, 0)
                                .orderByAsc(BizBomItem::getSortOrder)));
    }

    /**
     * 递归构造一个节点
     * @param materialId           本节点物料
     * @param requiredQty          本节点需求量 (上级算好的)
     * @param level                BOM 层级 (1=最顶级)
     * @param matCache             物料缓存
     * @param visiting             防 BOM 循环引用
     * @param parentBomVerId       父节点所在的 BOM 版本ID (用于嵌套展开同 BOM 内子件)
     * @param parentBomItemId      父 BOM_item ID (子件按 parent_item_id 取)
     * @param bomItemSourceType    本节点对应 BOM_item 的 sourceType (顶级时为 null, 此时用物料表的)
     */
    private Map<String, Object> buildNode(Long materialId, BigDecimal requiredQty, int level,
                                          Map<Long, BizMaterial> matCache, Set<Long> visiting,
                                          Long parentBomVerId, Long parentBomItemId,
                                          String bomItemSourceType) {
        BizMaterial material = matCache.computeIfAbsent(materialId, materialMapper::selectById);
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("materialId", materialId);
        node.put("materialNo", material != null ? material.getMaterialNo() : "");
        node.put("materialName", material != null ? material.getMaterialName() : "");
        node.put("spec", material != null ? material.getSpec() : "");
        node.put("unit", material != null ? material.getUnit() : "");
        node.put("level", level);
        node.put("requiredQty", requiredQty);

        // sourceType 决定:
        //   - level=1 顶级订单产品: 默认就是自制 (订单卖的就是要生产/出货的成品, 不读物料表免误判)
        //   - level>=2 子件: 用 BOM 项的 sourceType (同物料在不同 BOM 可能不同)
        boolean isManufacture;
        if (level == 1) {
            isManufacture = true;
        } else if (bomItemSourceType != null && !bomItemSourceType.isEmpty()) {
            isManufacture = "manufacture".equalsIgnoreCase(bomItemSourceType)
                    || "make".equalsIgnoreCase(bomItemSourceType);
        } else {
            String src = material != null && material.getSourceType() != null ? material.getSourceType() : "purchase";
            isManufacture = "manufacture".equalsIgnoreCase(src) || "make".equalsIgnoreCase(src);
        }
        node.put("sourceType", isManufacture ? "manufacture" : "purchase");

        // 安全库存垫底 = max(safetyStock, minStock) — 消耗订单后库存还需保留这么多
        BigDecimal safetyFloor = BigDecimal.ZERO;
        if (material != null) {
            BigDecimal safety = material.getSafetyStock() != null ? material.getSafetyStock() : BigDecimal.ZERO;
            BigDecimal minS = material.getMinStock() != null ? material.getMinStock() : BigDecimal.ZERO;
            safetyFloor = safety.max(minS);
        }
        // 缓存命中则复用, 否则查 DB 后塞 cache. 同 BOM 多处出现的物料只查一次, BOM 深时降一个量级.
        BigDecimal totalInventory = totalInventoryCache.get().computeIfAbsent(
                materialId, this::getTotalAvailableInventory);
        // 生产在途仅算"本订单"转的 WO (含顶级 + 任意层子件)
        BigDecimal inTransit = isManufacture
                ? productionInTransitCache.get().computeIfAbsent(
                        materialId, m -> getProductionInTransit(m, currentCalcOrderId.get()))
                : purchaseInTransitCache.get().computeIfAbsent(
                        materialId, this::getPurchaseInTransit);
        // 公式 A: 缺口 = max(0, 需求 + 最小库存 - 当前库存 - 在途)
        // 含义: 采购/生产完成后, 总供应(库存+在途+新增) 至少要满足 (订单需求 + 维持最小库存)
        // 边界: 当前库存 < 最小库存时, 缺口包含"补回最小库存差"+订单需求
        BigDecimal shortage = requiredQty.add(safetyFloor)
                                          .subtract(totalInventory)
                                          .subtract(inTransit)
                                          .max(BigDecimal.ZERO);
        // 展示用"可用库存" = 当前库存 - 最小库存 (给业务员看的"可消耗部分", 安全垫底不含在内)
        BigDecimal inventory = totalInventory.subtract(safetyFloor).max(BigDecimal.ZERO);
        node.put("inventoryQty", inventory);
        node.put("inTransitQty", inTransit);
        node.put("shortageQty", shortage);

        boolean selfSufficient = shortage.compareTo(BigDecimal.ZERO) <= 0;

        if (isManufacture) {
            // 防循环引用
            if (visiting.contains(materialId)) {
                node.put("status", "shortage");
                node.put("statusLabel", "BOM循环引用");
                node.put("canConvert", false);
                node.put("children", List.of());
                return node;
            }
            visiting.add(materialId);

            // 子件来源策略: 优先用"父 BOM 版本里 parent_item_id=父item.id"的嵌套子件;
            // 如果当前是顶级(parentBomVerId==null)或父节点不是 BOM 嵌套节点, 则查当前物料自己的 active BOM
            Long childBomVerId;
            Long childParentItemId;
            if (parentBomVerId != null && parentBomItemId != null) {
                // 嵌套展开: 取父 BOM 版本里 parent_item_id=父 item.id 的子件
                childBomVerId = parentBomVerId;
                childParentItemId = parentBomItemId;
            } else {
                // 顶级 / 独立 BOM: 查当前物料 active BOM 版本, 取顶级子件 (parent_item_id=0)
                BizBomVersion ver = bomVersionMapper.selectOne(
                        new LambdaQueryWrapper<BizBomVersion>()
                                .eq(BizBomVersion::getProductId, materialId)
                                .eq(BizBomVersion::getStatus, "active")
                                .eq(BizBomVersion::getDeleted, 0)
                                .orderByDesc(BizBomVersion::getId)
                                .last("LIMIT 1"));
                childBomVerId = ver != null ? ver.getId() : null;
                childParentItemId = 0L;
            }

            List<Map<String, Object>> children = new ArrayList<>();
            if (childBomVerId != null) {
                List<BizBomItem> allItems = getBomItemsByVersion(childBomVerId);
                List<BizBomItem> directChildren = new ArrayList<>();
                for (BizBomItem bi : allItems) {
                    Long pid = bi.getParentItemId() != null ? bi.getParentItemId() : 0L;
                    if (pid.equals(childParentItemId)) directChildren.add(bi);
                }
                // 子件需求量按"父级缺口(shortage)"算 — 父级库存+在途够了, 不需要再消耗子件
                // 父级 shortage=0 → 子件需求都是 0 → BOM 树虽然展开但所有子件状态都是充足
                BigDecimal effectiveQty = shortage;
                for (BizBomItem bi : directChildren) {
                    // 自制件 BOM item 的 materialId 可能为 null, 用 productId 作为半成品 ID
                    Long subMaterialId = bi.getMaterialId() != null ? bi.getMaterialId() : bi.getProductId();
                    if (subMaterialId == null) continue;
                    BigDecimal subPerUnit = bi.getActualQuantity() != null ? bi.getActualQuantity()
                            : (bi.getQuantity() != null ? bi.getQuantity() : BigDecimal.ZERO);
                    BigDecimal subRequired = effectiveQty.multiply(subPerUnit).setScale(4, RoundingMode.HALF_UP);
                    // 递归: 父 BOM 版本+父 item.id 用于嵌套展开下一层, 子件 sourceType 取自 BOM 项
                    Map<String, Object> childNode = buildNode(subMaterialId, subRequired, level + 1, matCache, visiting,
                            childBomVerId, bi.getId(), bi.getSourceType());
                    // 附加代替料选项(仅主料不足时有意义, 全量返回由前端过滤)
                    childNode.put("substituteOptions", fetchSubstituteOptions(bi.getId(), matCache));
                    children.add(childNode);
                }
            }
            visiting.remove(materialId);

            // 子节点视为"齐套"的两种情况:
            //   1) 子自己充足 (status=sufficient)
            //   2) 子是自制件、自己缺料但下级齐套可转 WO (canConvertWo=true)
            //      — 这种子级的子件库存已备好, 子件只需 release WO 即能产出, 对父级来说同样属于
            //      "下级已就绪"。原来只认 status=sufficient 太严, "下级齐套, 可转生产计划"
            //      的子也被误判成不齐套, 导致父级显示"下级未齐套"。
            boolean allChildrenSufficient = children.stream()
                    .allMatch(c -> "sufficient".equals(c.get("status"))
                            || Boolean.TRUE.equals(c.get("canConvertWo")));
            // 状态: 库存+在途够 → sufficient; 不够但下级齐套 → shortage_kit_ready (可转WO);
            // 不够且下级也缺 → shortage_kit_short (转WO置灰)
            String status;
            String label;
            boolean canConvertWo;
            if (selfSufficient) {
                status = "sufficient"; label = "充足"; canConvertWo = false;
            } else if (allChildrenSufficient) {
                status = "shortage_kit_ready"; label = "下级齐套, 可转生产计划"; canConvertWo = true;
            } else {
                status = "shortage_kit_short"; label = "下级未齐套"; canConvertWo = false;
            }
            node.put("status", selfSufficient ? "sufficient" : "shortage");
            node.put("statusLabel", label);
            node.put("canConvertWo", canConvertWo);
            node.put("canConvertPo", false);
            node.put("children", children);
        } else {
            // 外购件: 简单 充足 / 缺料
            node.put("status", selfSufficient ? "sufficient" : "shortage");
            node.put("statusLabel", selfSufficient ? "充足" : "缺料, 可转采购单");
            node.put("canConvertWo", false);
            node.put("canConvertPo", !selfSufficient);
            node.put("children", List.of());
        }

        // 顶级节点(订单产品本身)如果库存充足, 直接可发货
        // 不需要生产/采购, 业务员去订单详情发货即可
        node.put("canShip", level == 1 && selfSufficient);

        return node;
    }

    /**
     * 跨仓库"真正可用"库存合计, 三层扣减避免库存被多个订单/工单重复占用:
     *   1) SUM(total - locked) — 显式锁料
     *   2) - 已派发未完成出库任务剩余量 (sales_out / production_out / outsource_out 等)
     *   3) - 其他销售订单已确认未发货且尚未派发任务的承诺量 (quantity - shipped - taskQty)
     *
     * 排除当前正在计算的订单自己 (它的需求由 calculate() 里的 needed=quantity-shipped 表示)。
     */
    private BigDecimal getTotalAvailableInventory(Long materialId) {
        List<BizInventory> invs = inventoryMapper.selectList(
                new LambdaQueryWrapper<BizInventory>()
                        .eq(BizInventory::getMaterialId, materialId)
                        .eq(BizInventory::getDeleted, 0));
        BigDecimal sum = BigDecimal.ZERO;
        for (BizInventory inv : invs) {
            BigDecimal total = inv.getTotalQty() != null ? inv.getTotalQty() : BigDecimal.ZERO;
            BigDecimal locked = inv.getLockedQty() != null ? inv.getLockedQty() : BigDecimal.ZERO;
            sum = sum.add(total.subtract(locked).max(BigDecimal.ZERO));
        }
        // 第 2 层: 已派发未完成出库任务
        BigDecimal pendingOut = inventoryService.getPendingOutboundQty(materialId);
        if (pendingOut != null && pendingOut.signum() > 0) {
            sum = sum.subtract(pendingOut).max(BigDecimal.ZERO);
        }
        // 第 3 层: 其他销售订单已确认未发货 + 未派发任务的承诺量
        BigDecimal committed = getOtherOrdersCommittedDemand(materialId);
        if (committed != null && committed.signum() > 0) {
            sum = sum.subtract(committed).max(BigDecimal.ZERO);
        }
        // 兜底 1: 本订单工单已生产入库的部分物理上专属于本订单, 不应被其他订单承诺扣没.
        BigDecimal mySpecific = getCurrentOrderStockInSpecific(materialId);
        if (mySpecific.signum() > 0) {
            sum = sum.max(mySpecific);
        }
        // 兜底 2 (外购件): 本订单已通过 PR→PO→收料 链路补货的部分专属本订单
        BigDecimal myPurchased = getCurrentOrderPurchasedInSpecific(materialId);
        if (myPurchased.signum() > 0) {
            sum = sum.max(myPurchased);
        }
        return sum;
    }

    /**
     * 当前订单, 通过 PR→PO→收料 链路为本物料补货且尚未被本订单消耗的"专属库存"
     * 防止别的工单 pending production_out task 把"本订单专门买的料"吃光导致循环缺料
     *
     * 公式: SUM(po_item.receipt_qty WHERE po_item.request_id ∈ 本订单的 PR id 集)
     *      - SUM(本订单工单已实际领用本物料的量, 即已完成的 production_out task)
     */
    private BigDecimal getCurrentOrderPurchasedInSpecific(Long materialId) {
        Long curOrderId = currentCalcOrderId.get();
        if (curOrderId == null) return BigDecimal.ZERO;
        BizSalesOrder so = salesOrderMapper.selectById(curOrderId);
        if (so == null || so.getOrderNo() == null) return BigDecimal.ZERO;

        // 1) 本订单关联的 PR id 集 (sourceType=sales_order, sourceNo=订单号)
        Set<Long> myPrIds = purchaseRequestService.list(
                new LambdaQueryWrapper<com.aimes.modules.purchase.entity.BizPurchaseRequest>()
                        .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getDeleted, 0)
                        .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getMaterialId, materialId)
                        .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getSourceType, "sales_order")
                        .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getSourceNo, so.getOrderNo()))
                .stream().map(com.aimes.modules.purchase.entity.BizPurchaseRequest::getId)
                .collect(Collectors.toSet());
        if (myPrIds.isEmpty()) return BigDecimal.ZERO;

        // 2) 这些 PR 已收料总量 (PO 是否仍 open 不重要, 只关心实际到货量)
        BigDecimal received = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getDeleted, 0)
                        .eq(BizPurchaseOrderItem::getMaterialId, materialId)
                        .in(BizPurchaseOrderItem::getRequestId, myPrIds))
                .stream()
                .map(it -> it.getReceiptQty() != null ? it.getReceiptQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (received.signum() <= 0) return BigDecimal.ZERO;

        // 3) 减去本订单工单已领用的量 (本订单 WO 关联的 production_out task 的 processed_qty)
        BigDecimal consumed = computeOrderConsumedQty(curOrderId, materialId);
        return received.subtract(consumed).max(BigDecimal.ZERO);
    }

    /** 本订单所有工单累计领用本物料的量 (production_out task processed_qty 总和) */
    private BigDecimal computeOrderConsumedQty(Long orderId, Long materialId) {
        // 本订单所有工单 id
        Set<Long> myWoIds = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getOrderId, orderId)
                        .eq(BizWorkOrder::getDeleted, 0))
                .stream().map(BizWorkOrder::getId).collect(Collectors.toSet());
        if (myWoIds.isEmpty()) return BigDecimal.ZERO;
        // 本订单工单产生的 production_out task (含进行中和已完成)
        try {
            List<com.aimes.modules.inventory.entity.BizInvTask> tasks = invTaskMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInvTask>()
                            .eq(com.aimes.modules.inventory.entity.BizInvTask::getDeleted, 0)
                            .eq(com.aimes.modules.inventory.entity.BizInvTask::getInvType, "production_out")
                            .eq(com.aimes.modules.inventory.entity.BizInvTask::getSourceType, "work_order")
                            .in(com.aimes.modules.inventory.entity.BizInvTask::getSourceId, myWoIds));
            if (tasks.isEmpty()) return BigDecimal.ZERO;
            Set<Long> taskIds = tasks.stream()
                    .map(com.aimes.modules.inventory.entity.BizInvTask::getId)
                    .collect(Collectors.toSet());
            return invTaskItemMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInvTaskItem>()
                            .in(com.aimes.modules.inventory.entity.BizInvTaskItem::getTaskId, taskIds)
                            .eq(com.aimes.modules.inventory.entity.BizInvTaskItem::getMaterialId, materialId))
                    .stream()
                    .map(it -> it.getProcessedQty() != null ? it.getProcessedQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.warn("[MasterPlan] 算本订单已消耗失败 orderId={} mat={}: {}", orderId, materialId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * 当前正在计算的订单, 已通过工单生产入库且尚未发货的物料数量.
     * 这部分库存在 biz_inventory 里, 物理归属于本订单, 不应被其他订单的"未派工承诺"扣减.
     * 公式: SUM(work_order.stock_in_qty WHERE order_id=current AND product_id=mat) - shipped_qty(本订单本物料)
     */
    private BigDecimal getCurrentOrderStockInSpecific(Long materialId) {
        Long curOrderId = currentCalcOrderId.get();
        if (curOrderId == null) return BigDecimal.ZERO;
        // 本订单关联本物料的所有工单累计入库量
        List<BizWorkOrder> wos = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getOrderId, curOrderId)
                        .eq(BizWorkOrder::getProductId, materialId)
                        .eq(BizWorkOrder::getDeleted, 0));
        BigDecimal stockedIn = wos.stream()
                .map(w -> w.getStockInQty() != null ? w.getStockInQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (stockedIn.signum() <= 0) return BigDecimal.ZERO;
        // 减去本订单本物料已发货数量(这部分已不在库存里)
        List<BizSalesOrderItem> items = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>()
                        .eq(BizSalesOrderItem::getOrderId, curOrderId)
                        .eq(BizSalesOrderItem::getProductId, materialId)
                        .eq(BizSalesOrderItem::getDeleted, 0));
        BigDecimal shipped = items.stream()
                .map(i -> i.getShippedQty() != null ? i.getShippedQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return stockedIn.subtract(shipped).max(BigDecimal.ZERO);
    }

    /**
     * 查询某 BOM 子件的代替料列表, 并附上每种代替料的实时库存/在途.
     * 返回结果直接放进节点 substituteOptions 字段供前端展示和本地重算.
     */
    private List<Map<String, Object>> fetchSubstituteOptions(Long bomItemId, Map<Long, BizMaterial> matCache) {
        List<BizBomSubstitute> subs = bizBomSubstituteMapper.selectList(
                new LambdaQueryWrapper<BizBomSubstitute>()
                        .eq(BizBomSubstitute::getBomItemId, bomItemId)
                        .eq(BizBomSubstitute::getDeleted, 0)
                        .orderByAsc(BizBomSubstitute::getPriority));
        if (subs.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (BizBomSubstitute s : subs) {
            BizMaterial sm = matCache.computeIfAbsent(s.getMaterialId(), materialMapper::selectById);
            BigDecimal subInv = getTotalAvailableInventory(s.getMaterialId());
            boolean subIsManufacture = sm != null && ("manufacture".equalsIgnoreCase(sm.getSourceType())
                    || "make".equalsIgnoreCase(sm.getSourceType()));
            BigDecimal subTransit = subIsManufacture
                    ? getProductionInTransit(s.getMaterialId(), currentCalcOrderId.get())
                    : getPurchaseInTransit(s.getMaterialId(), currentCalcOrderId.get());
            BigDecimal ratio = s.getSubstituteRatio() != null && s.getSubstituteRatio().signum() > 0
                    ? s.getSubstituteRatio() : BigDecimal.ONE;
            Map<String, Object> opt = new LinkedHashMap<>();
            opt.put("substituteId", s.getId());
            opt.put("materialId", s.getMaterialId());
            opt.put("materialNo", sm != null ? sm.getMaterialNo() : "");
            opt.put("materialName", sm != null ? sm.getMaterialName() : "");
            opt.put("spec", sm != null ? sm.getSpec() : "");
            opt.put("unit", sm != null ? sm.getUnit() : "");
            opt.put("sourceType", sm != null ? sm.getSourceType() : "purchase");
            opt.put("priority", s.getPriority());
            opt.put("substituteRatio", ratio);
            opt.put("inventoryQty", subInv);
            opt.put("inTransitQty", subTransit);
            result.add(opt);
        }
        return result;
    }

    /**
     * 同物料下, 其他活跃销售订单(状态: confirmed/mrp_planned/in_production/outsourcing/partial_shipped)
     * 还没发货也没派发任务的剩余承诺量 = SUM(quantity - shipped - taskQty), 各项 max(0)
     * 排除当前正在算的那个订单 — 它的需求由 needed 字段直接表示, 不要重复扣。
     */
    private BigDecimal getOtherOrdersCommittedDemand(Long materialId) {
        Map<Long, BigDecimal> cache = committedDemandCache.get();
        if (cache.containsKey(materialId)) return cache.get(materialId);

        Long curOrderId = currentCalcOrderId.get();
        LambdaQueryWrapper<BizSalesOrderItem> w = new LambdaQueryWrapper<BizSalesOrderItem>()
                .eq(BizSalesOrderItem::getProductId, materialId)
                .eq(BizSalesOrderItem::getDeleted, 0);
        if (curOrderId != null) w.ne(BizSalesOrderItem::getOrderId, curOrderId);
        List<BizSalesOrderItem> items = salesOrderItemMapper.selectList(w);
        if (items.isEmpty()) { cache.put(materialId, BigDecimal.ZERO); return BigDecimal.ZERO; }

        Set<Long> orderIds = items.stream().map(BizSalesOrderItem::getOrderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> statusMap = orderIds.isEmpty() ? Map.of()
                : salesOrderMapper.selectBatchIds(orderIds).stream()
                        .collect(Collectors.toMap(BizSalesOrder::getId, BizSalesOrder::getStatus, (a, b) -> a));
        Set<String> activeStatuses = Set.of(
                "confirmed", "mrp_planned", "in_production", "outsourcing", "partial_shipped");

        BigDecimal sum = BigDecimal.ZERO;
        for (BizSalesOrderItem it : items) {
            String s = statusMap.get(it.getOrderId());
            if (s == null || !activeStatuses.contains(s)) continue;   // null 时 Set.of().contains 会 NPE
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal shipped = it.getShippedQty() != null ? it.getShippedQty() : BigDecimal.ZERO;
            BigDecimal taskQty = it.getTaskQty() != null ? it.getTaskQty() : BigDecimal.ZERO;
            BigDecimal pending = qty.subtract(shipped).subtract(taskQty);
            if (pending.signum() > 0) sum = sum.add(pending);
        }
        cache.put(materialId, sum);
        return sum;
    }

    /**
     * 采购在途 = SUM(quantity - receiptQty) WHERE PO 未关闭.
     * 设计: 在途严格按订单预占, 到货后进公共库存按"先派工先锁料"分配.
     *
     * 在途阶段 (本方法):
     *   只统计"为本订单下的申请所生成 PO 行项":
     *     PO_item.request_id → BizPurchaseRequest.source_no (订单号) == 本订单 order_no
     *   合并下发的 PO(一行 80 件来自不同订单)主计划重算时, 各订单只看到属于自己的那部分.
     *
     * 库存阶段 (getTotalAvailableInventory):
     *   货到了就归公共库存, 谁先派工单谁先 lock 谁先用 (现有 lockedQty 三层占用机制不变).
     */
    private BigDecimal getPurchaseInTransit(Long materialId) {
        return getPurchaseInTransit(materialId, currentCalcOrderId.get());
    }

    private BigDecimal getPurchaseInTransit(Long materialId, Long restrictOrderId) {
        List<BizPurchaseOrderItem> poItems = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getMaterialId, materialId)
                        .eq(BizPurchaseOrderItem::getDeleted, 0));
        if (poItems.isEmpty()) return BigDecimal.ZERO;
        Set<Long> poIds = poItems.stream().map(BizPurchaseOrderItem::getPurchaseOrderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizPurchaseOrder> poMap = poIds.isEmpty() ? Map.of()
                : purchaseOrderMapper.selectBatchIds(poIds).stream()
                        .collect(Collectors.toMap(BizPurchaseOrder::getId, p -> p, (a, b) -> a));
        Set<String> openStatuses = Set.of("draft", "pending_approval", "confirmed", "partial_received");

        // 限定订单模式: 预查本订单对应的申请 id 集合
        Set<Long> matchRequestIds = null;
        if (restrictOrderId != null) {
            BizSalesOrder so = salesOrderMapper.selectById(restrictOrderId);
            String orderNo = so != null ? so.getOrderNo() : null;
            if (orderNo == null || orderNo.isEmpty()) return BigDecimal.ZERO;
            matchRequestIds = new HashSet<>(purchaseRequestService.list(
                    new LambdaQueryWrapper<com.aimes.modules.purchase.entity.BizPurchaseRequest>()
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getDeleted, 0)
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getSourceType, "sales_order")
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getSourceNo, orderNo))
                    .stream().map(com.aimes.modules.purchase.entity.BizPurchaseRequest::getId)
                    .collect(Collectors.toSet()));
            if (matchRequestIds.isEmpty()) return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (BizPurchaseOrderItem it : poItems) {
            BizPurchaseOrder po = poMap.get(it.getPurchaseOrderId());
            if (po == null || !openStatuses.contains(po.getStatus())) continue;
            if (matchRequestIds != null) {
                if (it.getRequestId() == null || !matchRequestIds.contains(it.getRequestId())) continue;
            }
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal received = it.getReceiptQty() != null ? it.getReceiptQty() : BigDecimal.ZERO;
            sum = sum.add(qty.subtract(received).max(BigDecimal.ZERO));
        }

        // 补充: 本订单已生成但 PO 还没建的 PR(pending/approved 状态),
        // 让"刚转完 PR 还没下采购单"的部分也算入在途, 避免重算时显示缺料导致用户重复转
        // (issued 状态意味 PO 已建, 由上面 PO 行项接管)
        if (matchRequestIds != null && !matchRequestIds.isEmpty()) {
            for (com.aimes.modules.purchase.entity.BizPurchaseRequest pr : purchaseRequestService.list(
                    new LambdaQueryWrapper<com.aimes.modules.purchase.entity.BizPurchaseRequest>()
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getDeleted, 0)
                            .in(com.aimes.modules.purchase.entity.BizPurchaseRequest::getId, matchRequestIds)
                            .in(com.aimes.modules.purchase.entity.BizPurchaseRequest::getStatus,
                                    java.util.List.of("pending", "approved")))) {
                BigDecimal prQty = pr.getQuantity() != null ? pr.getQuantity() : BigDecimal.ZERO;
                sum = sum.add(prQty.max(BigDecimal.ZERO));
            }
        }
        return sum;
    }

    /**
     * 转生产计划: 根据节点信息创建一条草稿状态的生产计划单(单行)
     * @param substituteSelections 替代料选择 {materialId → substituteId}，可为 null
     * @return 新建的生产计划 ID, 前端可跳转过去查看
     */
    @Transactional
    public Long convertToProductionPlan(Long orderId, Long productId, BigDecimal qty,
                                        java.util.Map<Long, Long> substituteSelections) {
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("销售订单不存在");
        if (productId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("产品ID或数量不合法");
        }

        BizSalesOrderItem matchedItem = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>()
                        .eq(BizSalesOrderItem::getOrderId, orderId)
                        .eq(BizSalesOrderItem::getProductId, productId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);

        // 上限校验: 防止绕过前端 UI 直接调 API 创建超量草稿计划
        // 上限 = min(父级缺口, 子件支撑能力), 与前端 computeChildBreakdown 等价
        BigDecimal maxConvertable = computeMaxConvertableQty(orderId, productId, matchedItem);
        if (maxConvertable != null && qty.compareTo(maxConvertable) > 0) {
            throw new BusinessException("数量(" + qty.toPlainString() + ")超过最大可转("
                    + maxConvertable.toPlainString() + "), 受父级缺口或子件库存限制");
        }

        // 创建生产计划主表
        BizProductionPlan plan = new BizProductionPlan();
        plan.setPlanNo(CodeUtil.generate("PP"));
        plan.setPlanName("订单 " + order.getOrderNo() + " 主计划生成");
        plan.setStatus("draft");
        plan.setCreateType("master_plan");
        plan.setRemark("由订单主计划自动生成, 来源订单: " + order.getOrderNo());
        plan.setExtFields(order.getExtFields());
        LocalDate today = LocalDate.now();
        plan.setPlanDateFrom(today);
        plan.setPlanDateTo(order.getDeliveryDate() != null ? order.getDeliveryDate() : today.plusDays(7));
        productionPlanMapper.insert(plan);

        // 创建计划行项
        BizProductionPlanItem item = new BizProductionPlanItem();
        item.setPlanId(plan.getId());
        item.setProductId(productId);
        item.setPlanQty(qty);
        item.setReleasedQty(BigDecimal.ZERO);
        item.setCompletedQty(BigDecimal.ZERO);
        item.setPlanStartDate(plan.getPlanDateFrom());
        item.setPlanEndDate(plan.getPlanDateTo());
        item.setPriority(1);
        item.setSourceType("master_plan");
        item.setStatus("pending");
        if (substituteSelections != null && !substituteSelections.isEmpty()) {
            try {
                item.setSubstituteSelections(
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(substituteSelections));
            } catch (Exception e) {
                log.warn("[MasterPlan] 序列化替代料选择失败: {}", e.getMessage());
            }
        }
        productionPlanItemMapper.insert(item);

        // 关联到销售订单 (订单追溯)
        // 顶级产品: matchedItem 能在订单 items 里查到, orderItemId = item.id
        // BOM 子件: 子件 productId 不在订单 items 里, orderItemId 用 0L 占位
        //   (跟 BizProductionPlanService.java:781 的 fallback 一致)
        // 不管哪种情况都要建 rel, 否则下达 WO 时拿不到 orderId, WO 不挂订单 → 在途/追溯全失效
        BizProductionPlanOrderRel rel = new BizProductionPlanOrderRel();
        rel.setPlanItemId(item.getId());
        rel.setOrderId(orderId);
        rel.setOrderItemId(matchedItem != null ? matchedItem.getId() : 0L);
        rel.setAllocatedQty(qty);
        planOrderRelMapper.insert(rel);

        log.info("[MasterPlan] 订单 {} 自制件 productId={} qty={} → 生成生产计划 {}",
                order.getOrderNo(), productId, qty, plan.getPlanNo());
        return plan.getId();
    }

    /**
     * 转采购单: 根据节点信息创建一条草稿采购订单(单行)
     * 供应商默认从物料的 default_supplier_id 取
     * @return 新建采购订单 ID
     */
    /**
     * 主生产计划 → 转"采购申请"(草稿状态), 而非直接生成采购单.
     * 后续由用户在 [采购管理→采购申请] 编辑/提交审批 → 走采购订单标准流程
     */
    @Transactional
    public Long convertToPurchaseRequest(Long orderId, Long materialId, BigDecimal qty) {
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("销售订单不存在");
        if (materialId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("物料ID或数量不合法");
        }

        com.aimes.modules.master.entity.BizMaterial material = materialMapper.selectById(materialId);
        if (material == null) throw new BusinessException("物料不存在");

        com.aimes.modules.purchase.entity.BizPurchaseRequest pr = new com.aimes.modules.purchase.entity.BizPurchaseRequest();
        pr.setMaterialId(materialId);
        pr.setMaterialName(material.getMaterialName());
        pr.setSpec(material.getSpec());
        pr.setUnit(material.getUnit());
        pr.setQuantity(qty);
        pr.setSuggestedSupplierId(material.getDefaultSupplierId());
        pr.setEstimatedPrice(material.getStandardCost());
        // 预计到货日 = 今天 + 物料采购周期(没配则空, 留给采购员定)
        if (material.getPurchaseLeadTime() != null && material.getPurchaseLeadTime() > 0) {
            pr.setExpectedDate(LocalDate.now().plusDays(material.getPurchaseLeadTime()));
        }
        pr.setSourceType("sales_order");
        pr.setSourceNo(order.getOrderNo());
        pr.setRemark("由订单主计划自动生成, 来源订单: " + order.getOrderNo()
                + ", 物料: " + material.getMaterialNo());
        purchaseRequestService.createAsDraft(pr);

        log.info("[MasterPlan] 订单 {} 外购件 materialId={} qty={} → 生成草稿采购申请 {}",
                order.getOrderNo(), materialId, qty, pr.getRequestNo());
        return pr.getId();
    }

    /**
     * 生产在途 = SUM(planQty - completedQty) WHERE WO 未完工/未取消
     * @param restrictOrderId 限定 WO 所属销售订单 (null = 不限制)
     *   - 顶级订单产品(level=1): 传当前订单 id, 别的订单转的 WO 不算进本订单的在途
     *   - BOM 自制子件(level>1): 传 null, 子件 WO 一般不挂 orderId, 跨订单共享供应
     */
    private BigDecimal getProductionInTransit(Long materialId, Long restrictOrderId) {
        LambdaQueryWrapper<BizWorkOrder> wrapper = new LambdaQueryWrapper<BizWorkOrder>()
                .eq(BizWorkOrder::getProductId, materialId)
                .eq(BizWorkOrder::getDeleted, 0)
                .notIn(BizWorkOrder::getStatus, Arrays.asList("completed", "cancelled"));
        if (restrictOrderId != null) {
            wrapper.eq(BizWorkOrder::getOrderId, restrictOrderId);
        }
        List<BizWorkOrder> wos = workOrderMapper.selectList(wrapper);
        BigDecimal sum = BigDecimal.ZERO;
        for (BizWorkOrder wo : wos) {
            BigDecimal plan = wo.getPlanQty() != null ? wo.getPlanQty() : BigDecimal.ZERO;
            // 工单合格数 = MIN(所有工序合格数), 优先实时计算, fallback 用 wo.completedQty
            // 修复点: 数据模拟/批量操作可能没触发 wo.completedQty 同步, 这里实时算保证准确
            BigDecimal done = computeWoCompletedFromTasks(wo.getId());
            if (done == null) {
                done = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
            }
            sum = sum.add(plan.subtract(done).max(BigDecimal.ZERO));
        }
        return sum;
    }

    /**
     * 实时算工单合格完工数 = MIN(所有工序合格数), 反映工艺路线的瓶颈完工
     * 没工序时返回 null (调用方 fallback 到 wo.completedQty)
     */
    private BigDecimal computeWoCompletedFromTasks(Long workOrderId) {
        List<BizWoProcessTask> tasks = woProcessTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                        .eq(BizWoProcessTask::getDeleted, 0)
                        .ne(BizWoProcessTask::getStatus, "cancelled"));
        if (tasks.isEmpty()) return null;
        return tasks.stream()
                .map(t -> t.getQualifiedQty() != null ? t.getQualifiedQty() : BigDecimal.ZERO)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 算转生产计划时的"最大可转数量",与前端 computeChildBreakdown 等价。
     * 上限 = min(父级缺口, 子件支撑能力)
     *   - 父级缺口 = needed + safetyFloor - 库存 - 在途, 限 ≥0
     *   - 子件支撑 = MIN_over_children( (子件库存 + 子件在途) / 单耗 )
     * matchedItem 可能为 null (订单里没找到对应行项), 此时只用 BOM 子件能力做上限。
     */
    private BigDecimal computeMaxConvertableQty(Long orderId, Long productId, BizSalesOrderItem matchedItem) {
        currentCalcOrderId.set(orderId);
        committedDemandCache.remove();
        try {
            // 1) 父级缺口
            BigDecimal shortage = null;
            if (matchedItem != null) {
                BigDecimal need = (matchedItem.getQuantity() != null ? matchedItem.getQuantity() : BigDecimal.ZERO)
                        .subtract(matchedItem.getShippedQty() != null ? matchedItem.getShippedQty() : BigDecimal.ZERO);
                BizMaterial mat = materialMapper.selectById(productId);
                BigDecimal safetyFloor = BigDecimal.ZERO;
                if (mat != null) {
                    BigDecimal safety = mat.getSafetyStock() != null ? mat.getSafetyStock() : BigDecimal.ZERO;
                    BigDecimal minS = mat.getMinStock() != null ? mat.getMinStock() : BigDecimal.ZERO;
                    safetyFloor = safety.max(minS);
                }
                BigDecimal totalInv = getTotalAvailableInventory(productId);
                BigDecimal inTransit = getProductionInTransit(productId, orderId);
                shortage = need.add(safetyFloor).subtract(totalInv).subtract(inTransit).max(BigDecimal.ZERO);
            }

            // 2) 子件支撑能力
            BizBomVersion ver = bomVersionMapper.selectOne(
                    new LambdaQueryWrapper<BizBomVersion>()
                            .eq(BizBomVersion::getProductId, productId)
                            .eq(BizBomVersion::getStatus, "active")
                            .eq(BizBomVersion::getDeleted, 0)
                            .orderByDesc(BizBomVersion::getId)
                            .last("LIMIT 1"));
            BigDecimal childMax = null;
            if (ver != null) {
                List<BizBomItem> directChildren = bomItemMapper.selectList(
                        new LambdaQueryWrapper<BizBomItem>()
                                .eq(BizBomItem::getBomVersionId, ver.getId())
                                .eq(BizBomItem::getDeleted, 0)
                                .and(w -> w.eq(BizBomItem::getParentItemId, 0L).or().isNull(BizBomItem::getParentItemId)));
                for (BizBomItem bi : directChildren) {
                    Long subId = bi.getMaterialId() != null ? bi.getMaterialId() : bi.getProductId();
                    if (subId == null) continue;
                    BigDecimal perUnit = bi.getActualQuantity() != null ? bi.getActualQuantity()
                            : (bi.getQuantity() != null ? bi.getQuantity() : BigDecimal.ZERO);
                    if (perUnit.compareTo(BigDecimal.ZERO) <= 0) continue;
                    boolean childIsManufacture = "manufacture".equalsIgnoreCase(bi.getSourceType())
                            || "make".equalsIgnoreCase(bi.getSourceType());
                    BigDecimal childInv = getTotalAvailableInventory(subId);
                    BigDecimal childTransit = childIsManufacture
                            ? getProductionInTransit(subId, orderId)
                            : getPurchaseInTransit(subId);
                    BigDecimal supply = childInv.add(childTransit);
                    BigDecimal maxByChild = supply.divide(perUnit, 4, RoundingMode.FLOOR);
                    childMax = (childMax == null) ? maxByChild : childMax.min(maxByChild);
                }
            }

            // 取 min(shortage, childMax), null 跳过
            BigDecimal cap = shortage;
            if (childMax != null) {
                cap = (cap == null) ? childMax : cap.min(childMax);
            }
            return cap;
        } finally {
            currentCalcOrderId.remove();
            committedDemandCache.remove();
        }
    }
}

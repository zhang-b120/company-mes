package com.aimes.modules.production.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.inventory.entity.BizInvTask;
import com.aimes.modules.inventory.entity.BizInvTaskItem;
import com.aimes.modules.inventory.mapper.BizInvTaskItemMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskMapper;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 工序半成品入库 / 领料 服务
 *
 * 业务规则:
 *   - 每道工序报工后, 可选"半成品入库"沉淀产出到仓库
 *   - 入库的物料按 {父物料编号}_{工序名} 惰性建档, 类型 semi_finished, 首次建后绑定到 task.semiMaterialId
 *   - 入库数量 ≤ 工序合格数 - 已入库量(stocked - issued 不行, 入库要看产出累计, 不能因为下游领走就允许多入)
 *   - 下道工序需要先"领料"该半成品才能报工 (校验在 WorkReportService)
 *   - 撤销入库: 仅在该工序已入库量 > 已领走量 时允许 (剩余可撤的部分)
 *
 * 与 VirtualSemiProductService 的差异:
 *   - 后者服务外协链路, 用 VSM_ 前缀, 名称无分隔符
 *   - 本服务服务"工序间临时入库"流程, 用 SEMI_ 前缀, 名称 / 编号都用 _ 分隔, 跟用户配置一致
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemiStockService {

    private final BizMaterialMapper materialMapper;
    private final BizWoProcessTaskMapper taskMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final InventoryService inventoryService;
    private final BizInvTaskMapper invTaskMapper;
    private final BizInvTaskItemMapper invTaskItemMapper;

    /**
     * 半成品入库.
     *
     * @param taskId      工序任务 id
     * @param qty         本次入库数量 (必须 > 0, ≤ qualified - alreadyStocked)
     * @param warehouseId 入哪个仓库 (null 走默认半成品仓 998)
     * @return 入库后该工序的 semi_stocked_qty
     */
    /**
     * 申请半成品入库: 生成 biz_inv_task (inv_type=semi_in, status=pending),
     * 由仓管在"库存任务"里处理后, 真正落库 + 累加 task.semi_stocked_qty (走 onSemiStockInCompleted 回调).
     */
    @Transactional(rollbackFor = Exception.class)
    public Long stockIn(Long taskId, BigDecimal qty, Long warehouseId) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("入库数量必须大于 0");
        }
        if (warehouseId == null) {
            throw new BusinessException("请选择入库仓库");
        }
        BizWoProcessTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("工序任务不存在");
        BizWoProcessTask nextTask = findNextTask(task);
        if (nextTask == null) {
            throw new BusinessException("最后一道工序不需要半成品入库");
        }
        if (hasReported(nextTask)) {
            throw new BusinessException("下道工序已有报工记录, 上道工序无需再入半成品库");
        }

        BigDecimal qualified = task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO;
        if (qualified.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("该工序还没有合格数, 无法入半成品库");
        }
        BigDecimal stocked = task.getSemiStockedQty() != null ? task.getSemiStockedQty() : BigDecimal.ZERO;
        BigDecimal pending = getPendingSemiInQty(taskId);
        // 下道工序"直接产线消耗"数 (= 下道已报工累计 - 下道已领料数)
        // 用于防止本工序在下道已直接做完的部分上重复入库
        BigDecimal nextConsumed = computeNextDirectConsumed(task);
        BigDecimal canIn = qualified.subtract(stocked).subtract(pending).subtract(nextConsumed);
        if (canIn.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("已无可入库数量 (合格 " + qualified
                    + " - 已入 " + stocked + " - 申请中 " + pending
                    + " - 下道已直接消耗 " + nextConsumed + ")");
        }
        if (qty.compareTo(canIn) > 0) {
            throw new BusinessException("入库数量超过可入量: " + canIn
                    + " (合格 " + qualified + " - 已入 " + stocked
                    + " - 申请中 " + pending + " - 下道已直接消耗 " + nextConsumed + ")");
        }

        // 惰性建档半成品物料
        BizMaterial semiMat;
        if (task.getSemiMaterialId() != null) {
            semiMat = materialMapper.selectById(task.getSemiMaterialId());
            if (semiMat == null) throw new BusinessException("半成品物料丢失, 请联系管理员");
        } else {
            semiMat = getOrCreateSemiMaterial(task);
            task.setSemiMaterialId(semiMat.getId());
            // 首次入库时把所选仓库回填到物料默认仓, 后续撤销/领料默认走同一仓
            if (semiMat.getDefaultWarehouseId() == null) {
                semiMat.setDefaultWarehouseId(warehouseId);
                materialMapper.updateById(semiMat);
            }
            taskMapper.updateById(task);
        }

        BizWorkOrder wo = workOrderMapper.selectById(task.getWorkOrderId());
        // 创建库存任务
        BizInvTask invTask = new BizInvTask();
        invTask.setTaskNo(CodeUtil.generate("INK"));
        invTask.setInvType("semi_in");
        invTask.setRelatedOrderNo(wo != null ? wo.getWoNo() : null);
        invTask.setWarehouseId(warehouseId);
        invTask.setStatus("pending");
        invTask.setSourceType("wo_process_task");
        invTask.setSourceId(taskId);
        invTask.setApplicantId(SecurityUtil.getCurrentUserId());
        invTask.setRemark("工序[" + task.getStepNo() + "-" + task.getStepName() + "]半成品入库");
        invTaskMapper.insert(invTask);

        BizInvTaskItem item = new BizInvTaskItem();
        item.setTaskId(invTask.getId());
        item.setMaterialId(semiMat.getId());
        item.setMaterialNo(semiMat.getMaterialNo());
        item.setMaterialName(semiMat.getMaterialName());
        item.setSpec(semiMat.getSpec());
        item.setUnit(semiMat.getUnit());
        item.setQuantity(qty);
        item.setProcessedQty(BigDecimal.ZERO);
        item.setWarehouseId(warehouseId);
        invTaskItemMapper.insert(item);

        log.info("[SemiStock] 工序 {} 申请半成品入库任务 {}, 数量 {}", taskId, invTask.getTaskNo(), qty);
        return invTask.getId();
    }

    /**
     * 仓管处理完 semi_in 任务后的回调: 累加工序的 semi_stocked_qty.
     * 由 BizInvTaskService.process 在 inv_type=semi_in 且 source_type=wo_process_task 时调用.
     */
    @Transactional(rollbackFor = Exception.class)
    public void onSemiStockInCompleted(Long processTaskId, BigDecimal batchQty) {
        if (batchQty == null || batchQty.compareTo(BigDecimal.ZERO) <= 0) return;
        BizWoProcessTask task = taskMapper.selectById(processTaskId);
        if (task == null) return;
        BigDecimal stocked = task.getSemiStockedQty() != null ? task.getSemiStockedQty() : BigDecimal.ZERO;
        task.setSemiStockedQty(stocked.add(batchQty));
        taskMapper.updateById(task);
        log.info("[SemiStock] 工序 {} 半成品入库完成 {} 件 (累计 {})", processTaskId, batchQty, task.getSemiStockedQty());
    }

    /** 工序下"申请中(未完成)"的 semi_in 任务剩余量之和 */
    private BigDecimal getPendingSemiInQty(Long processTaskId) {
        List<BizInvTask> tasks = invTaskMapper.selectList(
                new LambdaQueryWrapper<BizInvTask>()
                        .eq(BizInvTask::getInvType, "semi_in")
                        .eq(BizInvTask::getSourceType, "wo_process_task")
                        .eq(BizInvTask::getSourceId, processTaskId)
                        .notIn(BizInvTask::getStatus, Arrays.asList("completed", "cancelled")));
        if (tasks.isEmpty()) return BigDecimal.ZERO;
        java.util.Set<Long> taskIds = tasks.stream().map(BizInvTask::getId).collect(java.util.stream.Collectors.toSet());
        List<BizInvTaskItem> items = invTaskItemMapper.selectList(
                new LambdaQueryWrapper<BizInvTaskItem>().in(BizInvTaskItem::getTaskId, taskIds));
        return items.stream()
                .map(it -> {
                    BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
                    BigDecimal p = it.getProcessedQty() != null ? it.getProcessedQty() : BigDecimal.ZERO;
                    BigDecimal r = q.subtract(p);
                    return r.compareTo(BigDecimal.ZERO) > 0 ? r : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 撤销入库 (按累计量回退). 仅当 stocked - issued >= qty 时允许 (剩余可撤).
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal revokeStockIn(Long taskId, BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("撤销数量必须大于 0");
        }
        BizWoProcessTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("工序任务不存在");
        if (task.getSemiMaterialId() == null) throw new BusinessException("该工序未发生过入库, 无法撤销");

        BigDecimal stocked = task.getSemiStockedQty() != null ? task.getSemiStockedQty() : BigDecimal.ZERO;
        BigDecimal issued = task.getSemiIssuedQty() != null ? task.getSemiIssuedQty() : BigDecimal.ZERO;
        BigDecimal revocable = stocked.subtract(issued);
        if (qty.compareTo(revocable) > 0) {
            throw new BusinessException("可撤数量不足, 仅剩 " + revocable
                    + " 件未被下道工序领走 (已领走 " + issued + " 件不可撤)");
        }

        BizMaterial semiMat = materialMapper.selectById(task.getSemiMaterialId());
        // 撤销默认走该半成品物料的默认仓 (= 当初入库选的仓), 业务不需要让用户再选
        if (semiMat == null || semiMat.getDefaultWarehouseId() == null) {
            throw new BusinessException("半成品物料无默认仓库, 无法定位撤销目标仓");
        }
        // 走"其他出库" 类型回退, related_type 标记是撤销
        inventoryService.stockOut(task.getSemiMaterialId(), semiMat.getDefaultWarehouseId(), qty,
                "other_out", null, "wo_process_task_revoke", taskId);

        task.setSemiStockedQty(stocked.subtract(qty));
        taskMapper.updateById(task);
        log.info("[SemiStock] 工序 {} 撤销入库 {} 件, 剩余累计 {}", taskId, qty, task.getSemiStockedQty());
        return task.getSemiStockedQty();
    }

    /**
     * 下道工序领料 (从上道工序的半成品库出库 + 累加上道 task.semi_issued_qty).
     *
     * @param currentTaskId 当前工序任务 id (下道, 即要领料的人)
     * @param qty           领料数量
     */
    /**
     * 申请半成品领料: 生成 biz_inv_task (inv_type=production_out, source_type=wo_process_task_issue, status=pending)
     * 由仓管在"库存任务"里处理后, 真正出库 + 累加上道工序的 semi_issued_qty (走 onSemiIssueCompleted 回调).
     */
    @Transactional(rollbackFor = Exception.class)
    public Long issueSemiMaterial(Long currentTaskId, BigDecimal qty, Long warehouseId) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("领料数量必须大于 0");
        }
        if (warehouseId == null) {
            throw new BusinessException("请选择出库仓库");
        }
        BizWoProcessTask current = taskMapper.selectById(currentTaskId);
        if (current == null) throw new BusinessException("工序任务不存在");

        BizWoProcessTask prev = findPrevTask(current);
        if (prev == null) throw new BusinessException("当前是首道工序, 无上道工序可领料");
        if (prev.getSemiMaterialId() == null || prev.getSemiStockedQty() == null
                || prev.getSemiStockedQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("上道工序未入半成品库, 无需领料 (直接报工即可)");
        }

        BigDecimal stocked = prev.getSemiStockedQty();
        BigDecimal issued = prev.getSemiIssuedQty() != null ? prev.getSemiIssuedQty() : BigDecimal.ZERO;
        BigDecimal pending = getPendingSemiIssueQty(currentTaskId);
        BigDecimal available = stocked.subtract(issued).subtract(pending);
        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("上道半成品库存已被领完或在申请中"
                    + " (已领 " + issued + ", 申请中 " + pending + ")");
        }
        if (qty.compareTo(available) > 0) {
            throw new BusinessException("可领 " + available + " 件 (库存 " + stocked
                    + " - 已领 " + issued + " - 申请中 " + pending + ")");
        }

        BizMaterial semiMat = materialMapper.selectById(prev.getSemiMaterialId());
        BizWorkOrder wo = workOrderMapper.selectById(current.getWorkOrderId());

        BizInvTask invTask = new BizInvTask();
        invTask.setTaskNo(CodeUtil.generate("INO"));
        invTask.setInvType("production_out");
        invTask.setRelatedOrderNo(wo != null ? wo.getWoNo() : null);
        invTask.setWarehouseId(warehouseId);
        invTask.setStatus("pending");
        invTask.setSourceType("wo_process_task_issue");
        invTask.setSourceId(currentTaskId);
        invTask.setApplicantId(SecurityUtil.getCurrentUserId());
        invTask.setRemark("工序[" + current.getStepNo() + "-" + current.getStepName()
                + "]领上道[" + prev.getStepNo() + "-" + prev.getStepName() + "]半成品");
        invTaskMapper.insert(invTask);

        BizInvTaskItem item = new BizInvTaskItem();
        item.setTaskId(invTask.getId());
        item.setMaterialId(semiMat.getId());
        item.setMaterialNo(semiMat.getMaterialNo());
        item.setMaterialName(semiMat.getMaterialName());
        item.setSpec(semiMat.getSpec());
        item.setUnit(semiMat.getUnit());
        item.setQuantity(qty);
        item.setProcessedQty(BigDecimal.ZERO);
        item.setWarehouseId(warehouseId);
        invTaskItemMapper.insert(item);

        log.info("[SemiStock] 工序 {} 申请半成品领料任务 {}, 数量 {}", currentTaskId, invTask.getTaskNo(), qty);
        return invTask.getId();
    }

    /**
     * 仓管处理完领料任务后的回调: 累加上道工序的 semi_issued_qty.
     * 由 BizInvTaskService.process 在 inv_type=production_out 且 source_type=wo_process_task_issue 时调用.
     */
    @Transactional(rollbackFor = Exception.class)
    public void onSemiIssueCompleted(Long currentTaskId, BigDecimal batchQty) {
        if (batchQty == null || batchQty.compareTo(BigDecimal.ZERO) <= 0) return;
        BizWoProcessTask current = taskMapper.selectById(currentTaskId);
        if (current == null) return;
        BizWoProcessTask prev = findPrevTask(current);
        if (prev == null) return;
        BigDecimal already = prev.getSemiIssuedQty() != null ? prev.getSemiIssuedQty() : BigDecimal.ZERO;
        prev.setSemiIssuedQty(already.add(batchQty));
        taskMapper.updateById(prev);
        log.info("[SemiStock] 工序 {} 领料完成 {} 件 (上道 {} 累计已领 {})",
                currentTaskId, batchQty, prev.getId(), prev.getSemiIssuedQty());
    }

    /** 工序下"申请中(未完成)"的领料任务剩余量之和 */
    private BigDecimal getPendingSemiIssueQty(Long currentTaskId) {
        List<BizInvTask> tasks = invTaskMapper.selectList(
                new LambdaQueryWrapper<BizInvTask>()
                        .eq(BizInvTask::getInvType, "production_out")
                        .eq(BizInvTask::getSourceType, "wo_process_task_issue")
                        .eq(BizInvTask::getSourceId, currentTaskId)
                        .notIn(BizInvTask::getStatus, Arrays.asList("completed", "cancelled")));
        if (tasks.isEmpty()) return BigDecimal.ZERO;
        java.util.Set<Long> taskIds = tasks.stream().map(BizInvTask::getId).collect(java.util.stream.Collectors.toSet());
        return invTaskItemMapper.selectList(
                new LambdaQueryWrapper<BizInvTaskItem>().in(BizInvTaskItem::getTaskId, taskIds))
                .stream()
                .map(it -> {
                    BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
                    BigDecimal p = it.getProcessedQty() != null ? it.getProcessedQty() : BigDecimal.ZERO;
                    BigDecimal r = q.subtract(p);
                    return r.compareTo(BigDecimal.ZERO) > 0 ? r : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 校验"工序是否可以开始报工".
     * 规则:
     *   1. 上道工序既没入半成品库也没在申请入库 → 自由报工 (沿用旧流程)
     *   2. 上道有 pending semi_in 任务(仓管还没处理) → 直接阻断, 等仓管处理完
     *   3. 上道已确认入库 (semi_stocked_qty > 0) 但本道还没领料 → 阻断, 必须先领料
     *   4. 已经领过料 → 本道累计报工(qualified + defect) ≤ 已领料数
     *
     * @return 阻止报工原因; null = 可以报工
     */
    public String checkReportBlocked(Long taskId) {
        BizWoProcessTask current = taskMapper.selectById(taskId);
        if (current == null) return null;
        BizWoProcessTask prev = findPrevTask(current);
        if (prev == null) return null;

        String prevName = prev.getStepName() != null ? prev.getStepName() : "";
        BigDecimal stocked = prev.getSemiStockedQty() != null ? prev.getSemiStockedQty() : BigDecimal.ZERO;
        BigDecimal pending = getPendingSemiInQty(prev.getId());

        // 规则 2: 上道有申请中的入库任务 → 锁
        if (pending.compareTo(BigDecimal.ZERO) > 0) {
            return "上道工序「" + prevName + "」已提交半成品入库任务 " + pending
                    + " 件 (待仓管处理), 处理完成前本工序不能报工";
        }
        // 规则 1: 上道既没入库也没在申请
        if (stocked.compareTo(BigDecimal.ZERO) <= 0) return null;
        // 规则 3a: 已申请领料但仓管还没处理 → 也阻断
        BigDecimal pendingIssue = getPendingSemiIssueQty(current.getId());
        if (pendingIssue.compareTo(BigDecimal.ZERO) > 0) {
            return "本工序已提交领料任务 " + pendingIssue + " 件 (待仓管处理), 处理完成前不能报工";
        }
        // 规则 3b: 上道已入库, 但本道还没领过料
        BigDecimal issued = prev.getSemiIssuedQty() != null ? prev.getSemiIssuedQty() : BigDecimal.ZERO;
        if (issued.compareTo(BigDecimal.ZERO) <= 0) {
            return "上道工序「" + prevName + "」已入半成品库 " + stocked + " 件, 本工序需先领料才能报工";
        }
        // 规则 4: 已领料 → 不在这里阻断 (具体由 input-number max 和 checkReportWithinIssued 卡)
        return null;
    }

    /**
     * 取当前工序的"本次报工最大允许数量". 用于前端 input-number max.
     * 返回 null = 不受半成品流程约束 (按 BOM/工艺路线常规上限);
     * 返回正数 = 本次最多能报的数量 = 已领料数 - 已累计报工.
     */
    public BigDecimal getMaxReportableByIssued(Long taskId) {
        BizWoProcessTask current = taskMapper.selectById(taskId);
        if (current == null) return null;
        BizWoProcessTask prev = findPrevTask(current);
        if (prev == null) return null;
        BigDecimal prevStocked = prev.getSemiStockedQty() != null ? prev.getSemiStockedQty() : BigDecimal.ZERO;
        if (prevStocked.compareTo(BigDecimal.ZERO) <= 0) return null;
        BigDecimal issued = prev.getSemiIssuedQty() != null ? prev.getSemiIssuedQty() : BigDecimal.ZERO;
        BigDecimal already = (current.getReportedQty() != null ? current.getReportedQty() : BigDecimal.ZERO)
                .add(current.getDefectQty() != null ? current.getDefectQty() : BigDecimal.ZERO);
        BigDecimal remain = issued.subtract(already);
        return remain.compareTo(BigDecimal.ZERO) > 0 ? remain : BigDecimal.ZERO;
    }

    /**
     * 报工提交时校验"本次报工后累计是否超过已领料数".
     * 仅在上道入过半成品库的场景生效 (其他场景沿用 BOM 上限 / 工艺路线上限校验).
     */
    public void checkReportWithinIssued(Long taskId, BigDecimal thisQualified, BigDecimal thisDefect) {
        BizWoProcessTask current = taskMapper.selectById(taskId);
        if (current == null) return;
        BizWoProcessTask prev = findPrevTask(current);
        if (prev == null) return;
        BigDecimal prevStocked = prev.getSemiStockedQty() != null ? prev.getSemiStockedQty() : BigDecimal.ZERO;
        if (prevStocked.compareTo(BigDecimal.ZERO) <= 0) return; // 没入库就没领料限制
        BigDecimal issued = prev.getSemiIssuedQty() != null ? prev.getSemiIssuedQty() : BigDecimal.ZERO;
        // 本道已累计报工 = reportedQty (含待质检) + defectQty
        BigDecimal already = (current.getReportedQty() != null ? current.getReportedQty() : BigDecimal.ZERO)
                .add(current.getDefectQty() != null ? current.getDefectQty() : BigDecimal.ZERO);
        BigDecimal thisTotal = (thisQualified != null ? thisQualified : BigDecimal.ZERO)
                .add(thisDefect != null ? thisDefect : BigDecimal.ZERO);
        BigDecimal afterTotal = already.add(thisTotal);
        if (afterTotal.compareTo(issued) > 0) {
            BigDecimal remain = issued.subtract(already).max(BigDecimal.ZERO);
            throw new BusinessException("本道工序累计报工 " + afterTotal
                    + " 件超过已领料数 " + issued + " 件, 本次最多可报 " + remain + " 件 (需先去领料才能继续报工)");
        }
    }

    // -------------------- 内部辅助 --------------------

    /** lazy 建档或返回已有半成品物料. 编号 SEMI_{父物料编号}_{工序名}; 名称 {父物料名}_{工序名} */
    private BizMaterial getOrCreateSemiMaterial(BizWoProcessTask task) {
        BizWorkOrder wo = workOrderMapper.selectById(task.getWorkOrderId());
        if (wo == null || wo.getProductId() == null) {
            throw new BusinessException("工单不存在或未关联产品, 无法创建半成品物料");
        }
        BizMaterial product = materialMapper.selectById(wo.getProductId());
        if (product == null) throw new BusinessException("父物料不存在");
        String stepName = task.getStepName() != null ? task.getStepName() : ("step" + task.getStepNo());
        String semiCode = "SEMI_" + product.getMaterialNo() + "_" + stepName;

        BizMaterial existing = materialMapper.selectOne(
                new LambdaQueryWrapper<BizMaterial>()
                        .eq(BizMaterial::getMaterialNo, semiCode)
                        .eq(BizMaterial::getDeleted, 0)
                        .last("LIMIT 1"));
        if (existing != null) return existing;

        BizMaterial semi = new BizMaterial();
        semi.setMaterialNo(semiCode);
        semi.setMaterialName(product.getMaterialName() + "_" + stepName);
        semi.setSpec(product.getSpec());
        semi.setCategory("semi_finished");
        semi.setCategoryId(2L);
        semi.setUnit(product.getUnit());
        semi.setIsVirtualSemi(1); // 跟外协虚拟物料一样不在物料档案列表展示 (列表 UI 已过滤)
        // defaultWarehouseId 不预设, 首次入库时由用户选仓库后回填
        semi.setStatus("active");
        semi.setSafetyStock(BigDecimal.ZERO);
        semi.setMrpEnabled(false);
        materialMapper.insert(semi);
        log.info("[SemiStock] 工序 {} 首次入库, 建档半成品 {} ({})", task.getId(), semi.getMaterialName(), semiCode);
        return semi;
    }

    /**
     * 算"下道工序已直接消耗本工序产出"的数量 = max(0, 下道报工累计 - 下道已领料累计).
     * 含义: 下道在没走半成品入库/领料的情况下, 直接产线流转消耗的本工序产出.
     * 这部分不能再被入半成品库 (会变成虚库存).
     */
    private BigDecimal computeNextDirectConsumed(BizWoProcessTask current) {
        BizWoProcessTask next = findNextTask(current);
        if (next == null) return BigDecimal.ZERO;
        // 下道报工累计 (有质检 reportedQty 已包含待检; 无质检从 qualifiedQty 走)
        BigDecimal nextReported = (next.getReportedQty() != null && next.getReportedQty().compareTo(BigDecimal.ZERO) > 0)
                ? next.getReportedQty()
                : (next.getQualifiedQty() != null ? next.getQualifiedQty() : BigDecimal.ZERO);
        BigDecimal nextDefect = next.getDefectQty() != null ? next.getDefectQty() : BigDecimal.ZERO;
        BigDecimal nextTotal = nextReported.add(nextDefect);
        BigDecimal nextIssued = current.getSemiIssuedQty() != null ? current.getSemiIssuedQty() : BigDecimal.ZERO;
        BigDecimal direct = nextTotal.subtract(nextIssued);
        return direct.compareTo(BigDecimal.ZERO) > 0 ? direct : BigDecimal.ZERO;
    }

    private boolean hasReported(BizWoProcessTask task) {
        BigDecimal reported = task.getReportedQty() != null ? task.getReportedQty() : BigDecimal.ZERO;
        BigDecimal qualified = task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO;
        BigDecimal defect = task.getDefectQty() != null ? task.getDefectQty() : BigDecimal.ZERO;
        return reported.compareTo(BigDecimal.ZERO) > 0
                || qualified.compareTo(BigDecimal.ZERO) > 0
                || defect.compareTo(BigDecimal.ZERO) > 0;
    }

    /** 找当前工序的下道工序 (同工单 stepNo 最小且 > 当前 stepNo) */
    private BizWoProcessTask findNextTask(BizWoProcessTask current) {
        if (current.getWorkOrderId() == null || current.getStepNo() == null) return null;
        List<BizWoProcessTask> peers = taskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, current.getWorkOrderId())
                        .eq(BizWoProcessTask::getDeleted, 0)
                        .gt(BizWoProcessTask::getStepNo, current.getStepNo())
                        .ne(BizWoProcessTask::getStatus, "cancelled")
                        .orderByAsc(BizWoProcessTask::getStepNo)
                        .last("LIMIT 1"));
        return peers.isEmpty() ? null : peers.get(0);
    }

    /** 找当前工序的上道工序 (同工单 stepNo 最大且 < 当前 stepNo) */
    private BizWoProcessTask findPrevTask(BizWoProcessTask current) {
        if (current.getWorkOrderId() == null || current.getStepNo() == null) return null;
        List<BizWoProcessTask> peers = taskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, current.getWorkOrderId())
                        .eq(BizWoProcessTask::getDeleted, 0)
                        .lt(BizWoProcessTask::getStepNo, current.getStepNo())
                        .ne(BizWoProcessTask::getStatus, "cancelled")
                        .orderByDesc(BizWoProcessTask::getStepNo)
                        .last("LIMIT 1"));
        return peers.isEmpty() ? null : peers.get(0);
    }

    /** 工序对外暴露的半成品状态摘要 (前端报工卡片用) */
    public Optional<BizWoProcessTask> getTaskMeta(Long taskId) {
        return Optional.ofNullable(taskMapper.selectById(taskId));
    }
}

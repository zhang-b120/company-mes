package com.aimes.modules.production.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import lombok.extern.slf4j.Slf4j;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.production.entity.BizReworkOrder;
import com.aimes.modules.production.entity.BizReworkOrderStep;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizReworkOrderMapper;
import com.aimes.modules.production.mapper.BizReworkOrderStepMapper;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BizReworkOrderService extends ServiceImpl<BizReworkOrderMapper, BizReworkOrder> {

    private final BizReworkOrderStepMapper stepMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizMaterialMapper materialMapper;
    private final SysUserMapper userMapper;
    private final com.aimes.modules.quality.mapper.BizQcTaskMapper qcTaskMapper;
    private final com.aimes.modules.bom.mapper.BizProcessMapper processMapper;
    private final com.aimes.modules.inventory.service.BizInvTaskService invTaskService;
    private final com.aimes.modules.system.service.SysNotificationService notificationService;
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.aimes.modules.workreport.service.WorkReportService workReportService;
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.aimes.modules.outsource.service.OutsourceChainService outsourceChainService;

    public PageResult<Map<String, Object>> getPage(String reworkNo, String status, int pageNum, int pageSize) {
        return getPage(reworkNo, null, null, null, null, null, status, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String reworkNo, String workOrderNo, String qcTaskNo,
                                                    String materialNo, String materialName, String spec,
                                                    String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizReworkOrder> wrapper = new LambdaQueryWrapper<BizReworkOrder>()
                .like(StringUtils.hasText(reworkNo), BizReworkOrder::getReworkNo, reworkNo)
                .eq(StringUtils.hasText(status), BizReworkOrder::getStatus, status)
                .orderByDesc(BizReworkOrder::getCreateTime);

        // 工单号过滤
        if (StringUtils.hasText(workOrderNo)) {
            List<Long> woIdList = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>().like(BizWorkOrder::getWoNo, workOrderNo))
                    .stream().map(BizWorkOrder::getId).collect(Collectors.toList());
            if (woIdList.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            wrapper.in(BizReworkOrder::getWorkOrderId, woIdList);
        }
        // 质检单号过滤
        if (StringUtils.hasText(qcTaskNo)) {
            List<Long> qcIdList = qcTaskMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTask>()
                            .like(com.aimes.modules.quality.entity.BizQcTask::getTaskNo, qcTaskNo))
                    .stream().map(com.aimes.modules.quality.entity.BizQcTask::getId).collect(Collectors.toList());
            if (qcIdList.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            wrapper.in(BizReworkOrder::getQcTaskId, qcIdList);
        }
        // 物料(产品)过滤: 查物料表 → 拿到 ids → productId / materialId 任一命中
        // 支持 spec 单值与复合输入
        if (StringUtils.hasText(materialNo) || StringUtils.hasText(materialName) || StringUtils.hasText(spec)) {
            LambdaQueryWrapper<BizMaterial> mw = new LambdaQueryWrapper<BizMaterial>()
                    .like(StringUtils.hasText(materialNo), BizMaterial::getMaterialNo, materialNo)
                    .like(StringUtils.hasText(materialName), BizMaterial::getMaterialName, materialName);
            com.aimes.common.utils.SpecQueryUtil.applyToBizMaterial(mw, spec);
            List<Long> mIds = materialMapper.selectList(mw).stream()
                    .map(BizMaterial::getId).collect(Collectors.toList());
            if (mIds.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            wrapper.and(w -> w.in(BizReworkOrder::getProductId, mIds).or().in(BizReworkOrder::getMaterialId, mIds));
        }
        Page<BizReworkOrder> page = page(new Page<>(pageNum, pageSize), wrapper);

        Set<Long> woIds = page.getRecords().stream().map(BizReworkOrder::getWorkOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> qcIds = page.getRecords().stream().map(BizReworkOrder::getQcTaskId).filter(Objects::nonNull).collect(Collectors.toSet());
        // 收集物料ID：优先从productId，没有则用materialId
        Set<Long> allMatIds = new HashSet<>();
        page.getRecords().forEach(r -> {
            if (r.getProductId() != null) allMatIds.add(r.getProductId());
            if (r.getMaterialId() != null) allMatIds.add(r.getMaterialId());
        });
        Map<Long, String> woNoMap = woIds.isEmpty() ? new HashMap<>() :
                workOrderMapper.selectBatchIds(woIds).stream().collect(Collectors.toMap(BizWorkOrder::getId, BizWorkOrder::getWoNo, (a, b) -> a));
        Map<Long, String> qcNoMap = qcIds.isEmpty() ? new HashMap<>() :
                qcTaskMapper.selectBatchIds(qcIds).stream().collect(Collectors.toMap(com.aimes.modules.quality.entity.BizQcTask::getId, com.aimes.modules.quality.entity.BizQcTask::getTaskNo, (a, b) -> a));
        Map<Long, BizMaterial> matMap = allMatIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(allMatIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        List<Map<String, Object>> list = page.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("reworkNo", r.getReworkNo());
            m.put("workOrderId", r.getWorkOrderId());
            m.put("woNo", r.getWorkOrderId() != null ? woNoMap.getOrDefault(r.getWorkOrderId(), "") : "");
            m.put("qcTaskId", r.getQcTaskId());
            m.put("qcTaskNo", r.getQcTaskId() != null ? qcNoMap.getOrDefault(r.getQcTaskId(), "") : "");
            Long matId = r.getProductId() != null ? r.getProductId() : r.getMaterialId();
            BizMaterial mat = matId != null ? matMap.get(matId) : null;
            m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            m.put("materialName", mat != null ? mat.getMaterialName() : "");
            m.put("spec", mat != null && mat.getSpec() != null ? mat.getSpec() : "");
            m.put("quantity", r.getQuantity());
            m.put("status", r.getStatus());
            m.put("remark", r.getRemark());
            m.put("createTime", r.getCreateTime());
            return m;
        }).toList();
        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    public Map<String, Object> getDetail(Long id) {
        BizReworkOrder r = getById(id);
        if (r == null) throw new BusinessException("返修工单不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("reworkNo", r.getReworkNo());
        m.put("workOrderId", r.getWorkOrderId());
        m.put("qcTaskId", r.getQcTaskId());
        m.put("qcDisposalId", r.getQcDisposalId());
        m.put("processTaskId", r.getProcessTaskId());
        m.put("quantity", r.getQuantity());
        m.put("status", r.getStatus());
        m.put("remark", r.getRemark());
        m.put("createTime", r.getCreateTime());

        BizWorkOrder wo = r.getWorkOrderId() != null ? workOrderMapper.selectById(r.getWorkOrderId()) : null;
        m.put("woNo", wo != null ? wo.getWoNo() : "");
        BizMaterial mat = r.getMaterialId() != null ? materialMapper.selectById(r.getMaterialId()) : null;
        m.put("materialName", mat != null ? mat.getMaterialName() : "");

        // 工序列表
        List<BizReworkOrderStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<BizReworkOrderStep>()
                        .eq(BizReworkOrderStep::getReworkOrderId, id)
                        .orderByAsc(BizReworkOrderStep::getSortOrder));
        Set<Long> userIds = steps.stream().map(BizReworkOrderStep::getAssignedUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        List<Map<String, Object>> stepList = steps.stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.getId());
            sm.put("processId", s.getProcessId());
            sm.put("stepName", s.getStepName());
            sm.put("assignedUserId", s.getAssignedUserId());
            sm.put("assignedUserName", s.getAssignedUserId() != null ? userNameMap.getOrDefault(s.getAssignedUserId(), "") : "");
            sm.put("sortOrder", s.getSortOrder());
            sm.put("status", s.getStatus());
            sm.put("qcTaskId", s.getQcTaskId());
            sm.put("completedAt", s.getCompletedAt());
            return sm;
        }).toList();
        m.put("steps", stepList);
        return m;
    }

    /**
     * 创建返修工单（由质检处理单调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createReworkOrder(BizReworkOrder order, List<Map<String, Object>> steps) {
        order.setReworkNo(CodeUtil.generate("RW"));
        order.setStatus("pending");
        save(order);

        if (steps != null) {
            int sortOrder = 1;
            for (Map<String, Object> stepData : steps) {
                BizReworkOrderStep step = new BizReworkOrderStep();
                step.setReworkOrderId(order.getId());
                step.setProcessId(stepData.get("processId") != null ? Long.valueOf(stepData.get("processId").toString()) : null);
                step.setProcessTaskId(stepData.get("processTaskId") != null ? Long.valueOf(stepData.get("processTaskId").toString()) : null);
                step.setStepName(stepData.get("stepName") != null ? stepData.get("stepName").toString() : null);
                step.setAssignedUserId(stepData.get("assignedUserId") != null ? Long.valueOf(stepData.get("assignedUserId").toString()) : null);
                step.setSortOrder(sortOrder++);
                step.setStatus("pending");
                stepMapper.insert(step);
            }
        }
        return order.getId();
    }

    /**
     * 完成单道工序. 严格串行:
     *   1. sortOrder 小于本工序的所有工序必须已 completed (不能跳过/插队)
     *   2. 该工序对应 BizProcess 配了 qcTemplateId → 建质检任务, step.status='qc_pending', 等质检通过才能下一道
     *   3. 没配 qcTemplateId → step.status='completed', 下一道可立即开始
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeStep(Long reworkOrderId, Long stepId) {
        BizReworkOrder order = getById(reworkOrderId);
        if (order == null) throw new BusinessException("返修工单不存在");
        if ("completed".equals(order.getStatus())) throw new BusinessException("返修工单已完成");

        BizReworkOrderStep step = stepMapper.selectById(stepId);
        if (step == null || !step.getReworkOrderId().equals(reworkOrderId)) throw new BusinessException("工序不存在");
        if (!"pending".equals(step.getStatus())) {
            throw new BusinessException("工序状态为 [" + step.getStatus() + "], 不能再次完成");
        }

        // 前置工序串行校验
        List<BizReworkOrderStep> prevSteps = stepMapper.selectList(
                new LambdaQueryWrapper<BizReworkOrderStep>()
                        .eq(BizReworkOrderStep::getReworkOrderId, reworkOrderId)
                        .eq(BizReworkOrderStep::getDeleted, 0)
                        .lt(BizReworkOrderStep::getSortOrder, step.getSortOrder() != null ? step.getSortOrder() : 0));
        for (BizReworkOrderStep prev : prevSteps) {
            if (!"completed".equals(prev.getStatus())) {
                throw new BusinessException("前置工序「" + prev.getStepName() + "」未完成 (状态: "
                        + prev.getStatus() + "), 不能跳过");
            }
        }

        // 看本工序是否要质检
        // 优先级: 1) 返修工序绑定的原工单工序(processTaskId) 2) 返修工序自身processId 3) 返修工单关联的原工序
        Long qcTemplateId = null;
        if (step.getProcessTaskId() != null) {
            BizWoProcessTask linkedTask = processTaskMapper.selectById(step.getProcessTaskId());
            if (linkedTask != null) qcTemplateId = linkedTask.getQcTemplateId();
        }
        if (qcTemplateId == null && step.getProcessId() != null) {
            com.aimes.modules.bom.entity.BizProcess proc = processMapper.selectById(step.getProcessId());
            if (proc != null) qcTemplateId = proc.getQcTemplateId();
        }
        if (qcTemplateId == null && order.getProcessTaskId() != null) {
            BizWoProcessTask origTask = processTaskMapper.selectById(order.getProcessTaskId());
            if (origTask != null) qcTemplateId = origTask.getQcTemplateId();
        }
        if (qcTemplateId != null) {
            // 建质检任务, 工序进入 qc_pending
            com.aimes.modules.quality.entity.BizQcTask qt = new com.aimes.modules.quality.entity.BizQcTask();
            qt.setTaskNo(CodeUtil.generate("QC"));
            qt.setQcType("rework");
            qt.setProductId(order.getProductId());
            qt.setMaterialId(order.getMaterialId() != null ? order.getMaterialId() : order.getProductId());
            qt.setTemplateId(qcTemplateId);
            qt.setRelatedOrderType("rework_order");
            qt.setRelatedOrderId(order.getId());
            qt.setWorkOrderId(order.getWorkOrderId());
            qt.setTaskId(step.getProcessTaskId() != null ? step.getProcessTaskId() : order.getProcessTaskId());
            qt.setPlanQty(order.getQuantity());
            qt.setPassQty(java.math.BigDecimal.ZERO);
            qt.setFailQty(java.math.BigDecimal.ZERO);
            qt.setResult("pending");
            qt.setDisposalStatus("none");
            qt.setRemark("返修单[" + order.getReworkNo() + "] 工序[" + step.getStepName() + "] 完工质检");
            qcTaskMapper.insert(qt);
            step.setStatus("qc_pending");
            step.setQcTaskId(qt.getId());
        } else {
            step.setStatus("completed");
            step.setCompletedAt(LocalDateTime.now());
        }
        stepMapper.updateById(step);

        if (!"in_progress".equals(order.getStatus())) {
            order.setStatus("in_progress");
            updateById(order);
        }

        // 检查是否所有工序 completed (无质检的话能立即推进)
        checkAndComplete(reworkOrderId);
    }

    /**
     * 由 BizQcTaskService 在 rework 类型质检完成时回调 (无论 pass 还是 fail).
     *   1. 把 step.status 从 qc_pending 推到 completed
     *   2. 触发 checkAndComplete — 整单完成时按"各 step 的 passQty 取 min"作为 restore 数量
     *      (fail 那部分件由 disposal 流程后续单独处理, 不重复加)
     */
    @Transactional(rollbackFor = Exception.class)
    public void onReworkStepQcCompleted(Long qcTaskId) {
        BizReworkOrderStep step = stepMapper.selectOne(
                new LambdaQueryWrapper<BizReworkOrderStep>()
                        .eq(BizReworkOrderStep::getQcTaskId, qcTaskId)
                        .eq(BizReworkOrderStep::getDeleted, 0)
                        .last("LIMIT 1"));
        if (step == null) return;
        if ("completed".equals(step.getStatus())) return;
        step.setStatus("completed");
        step.setCompletedAt(LocalDateTime.now());
        stepMapper.updateById(step);
        checkAndComplete(step.getReworkOrderId());
    }

    /**
     * 整单完成: 等价于"按 sortOrder 顺序对所有 pending 工序逐道调 completeStep".
     * 遇到有质检模板的工序会变 qc_pending → 在此停下, 后续等质检通过自动推进.
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(Long reworkOrderId) {
        BizReworkOrder order = getById(reworkOrderId);
        if (order == null) throw new BusinessException("返修工单不存在");
        if ("completed".equals(order.getStatus())) throw new BusinessException("返修工单已完成");

        List<BizReworkOrderStep> pendingSteps = stepMapper.selectList(
                new LambdaQueryWrapper<BizReworkOrderStep>()
                        .eq(BizReworkOrderStep::getReworkOrderId, reworkOrderId)
                        .eq(BizReworkOrderStep::getDeleted, 0)
                        .eq(BizReworkOrderStep::getStatus, "pending")
                        .orderByAsc(BizReworkOrderStep::getSortOrder));
        if (pendingSteps.isEmpty()) {
            // 已经无 pending 工序了, 调一次 checkAndComplete 兜底
            checkAndComplete(reworkOrderId);
            return;
        }
        // 逐道完成: completeStep 内部会做前置工序串行校验 + 质检判定
        for (BizReworkOrderStep s : pendingSteps) {
            // 重新读最新状态, 防止上一步把当前状态推到 qc_pending 后面影响
            BizReworkOrderStep fresh = stepMapper.selectById(s.getId());
            if (fresh == null || !"pending".equals(fresh.getStatus())) continue;
            completeStep(reworkOrderId, s.getId());
            // 如果完成后这一步成了 qc_pending, 后续工序必须等质检通过, 这里停下
            BizReworkOrderStep after = stepMapper.selectById(s.getId());
            if (after != null && "qc_pending".equals(after.getStatus())) {
                break;
            }
        }
    }

    private void checkAndComplete(Long reworkOrderId) {
        List<BizReworkOrderStep> allSteps = stepMapper.selectList(
                new LambdaQueryWrapper<BizReworkOrderStep>()
                        .eq(BizReworkOrderStep::getReworkOrderId, reworkOrderId));
        boolean allDone = allSteps.stream().allMatch(s -> "completed".equals(s.getStatus()));
        if (!allDone) return;

        BizReworkOrder order = getById(reworkOrderId);
        // 防重复 restore: 用 remark 标记
        if (order.getRemark() != null && order.getRemark().contains("[QC_RESTORED]")) {
            if (!"completed".equals(order.getStatus())) {
                order.setStatus("completed");
                updateById(order);
            }
            return;
        }

        // 取各 step 的"实际合格"取 min 作为 restore 数量
        // 无质检的 step 用 order.quantity; 有质检的 step 用 passQty
        BigDecimal restoreQty = order.getQuantity() != null ? order.getQuantity() : BigDecimal.ZERO;
        for (BizReworkOrderStep s : allSteps) {
            if (s.getQcTaskId() == null) continue;
            com.aimes.modules.quality.entity.BizQcTask t = qcTaskMapper.selectById(s.getQcTaskId());
            BigDecimal pass = (t != null && t.getPassQty() != null) ? t.getPassQty() : BigDecimal.ZERO;
            if (pass.compareTo(restoreQty) < 0) restoreQty = pass;
        }
        log.info("返修工单 {} 全部工序完成, restoreQty={}, processTaskId={}", order.getReworkNo(), restoreQty, order.getProcessTaskId());

        Long restoreWorkOrderId = null;
        if (restoreQty.compareTo(BigDecimal.ZERO) > 0) {
            restoreWorkOrderId = restoreQualifiedQty(order, restoreQty);
        }

        order.setStatus("completed");
        order.setRemark((order.getRemark() != null ? order.getRemark() : "") + "[QC_RESTORED]");
        updateById(order);

        // 确保 work order 进度同步（不依赖 restoreQualifiedQty 内的 try-catch）
        if (restoreWorkOrderId == null && order.getWorkOrderId() != null) {
            restoreWorkOrderId = order.getWorkOrderId();
        }
        if (restoreWorkOrderId != null) {
            try {
                workReportService.updateWorkOrderProgress(restoreWorkOrderId);
                log.info("返修完成后同步工单进度: workOrderId={}", restoreWorkOrderId);
            } catch (Exception e) {
                log.error("返修完成后同步工单进度失败: workOrderId={}", restoreWorkOrderId, e);
            }
        }
    }

    /**
     * 返修完成后恢复关联工单工序的合格数量,并交给统一的入库评估器处理。
     * 不再单独按 reworkQty 直接开一条入库任务 — 那样会绕过"全工序 min(qualifiedQty) - 已开入库"
     * 的口径,导致用户看到孤立的"返修数量"入库按钮。统一走 tryCreateLastStepStockIn 后:
     *   - 如果产品瓶颈因返修而上升(其它工序合格数 ≥ 这道工序),自然按差额开/追加入库
     *   - 如果其它工序还没跟上,delta ≤ 0 不开,等其它工序到位再开
     */
    /** 兼容旧调用: 默认按 order.quantity 全量回写 */
    private Long restoreQualifiedQty(BizReworkOrder order) {
        return restoreQualifiedQty(order, order.getQuantity() != null ? order.getQuantity() : BigDecimal.ZERO);
    }

    /** 按指定数量回写到原工单工序 (严格模式下 qty = 各 step passQty 的 min), 返回 workOrderId */
    private Long restoreQualifiedQty(BizReworkOrder order, BigDecimal reworkQty) {
        if (order.getProcessTaskId() == null) {
            log.warn("返修工单 {} 的 processTaskId 为空, 跳过合格数回写", order.getReworkNo());
            return order.getWorkOrderId();
        }
        if (reworkQty == null || reworkQty.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("返修工单 {} restoreQty={}, 跳过合格数回写", order.getReworkNo(), reworkQty);
            return order.getWorkOrderId();
        }
        BizWoProcessTask task = processTaskMapper.selectById(order.getProcessTaskId());
        if (task == null) {
            log.warn("返修工单 {} processTaskId={} 对应工序任务不存在", order.getReworkNo(), order.getProcessTaskId());
            return order.getWorkOrderId();
        }
        BigDecimal current = task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO;
        task.setQualifiedQty(current.add(reworkQty));
        // 若合格数已达到计划数, 将工序推进到 completed
        BigDecimal planQty = task.getPlanQty() != null ? task.getPlanQty() : BigDecimal.ZERO;
        boolean taskBecameCompleted = false;
        if (!"completed".equals(task.getStatus()) && task.getQualifiedQty().compareTo(planQty) >= 0 && planQty.compareTo(BigDecimal.ZERO) > 0) {
            task.setStatus("completed");
            taskBecameCompleted = true;
            log.info("返修工单 {} 工序[{}] qualifiedQty 已达 planQty={}, 状态推进为 completed", order.getReworkNo(), task.getStepName(), planQty);
        }
        processTaskMapper.updateById(task);
        log.info("返修工单 {} 回写工序[{}] qualifiedQty: {} -> {}", order.getReworkNo(), task.getStepName(), current, task.getQualifiedQty());

        if (task.getWorkOrderId() != null) {
            if (taskBecameCompleted) {
                try {
                    workReportService.activateNextTask(task.getWorkOrderId(), task.getStepNo());
                    log.info("返修完成后激活下一工序: workOrderId={}, stepNo={}", task.getWorkOrderId(), task.getStepNo());
                } catch (Exception e) {
                    log.error("返修完成后激活下一工序失败: workOrderId={}", task.getWorkOrderId(), e);
                }
            }
            // 触发外协链路：若下一工序是外协工序，补充虚拟半成品入库并更新外协加工单数量
            try {
                outsourceChainService.handlePrevStepReported(task.getWorkOrderId(), task, reworkQty);
                log.info("返修完成后触发外协链路: workOrderId={}, stepNo={}, qty={}", task.getWorkOrderId(), task.getStepNo(), reworkQty);
            } catch (Exception e) {
                log.error("返修完成后触发外协链路失败: workOrderId={}", task.getWorkOrderId(), e);
            }
            try {
                workReportService.updateWorkOrderProgress(task.getWorkOrderId());
            } catch (Exception e) {
                log.error("返修完成后更新工单进度失败: workOrderId={}", task.getWorkOrderId(), e);
            }
            try {
                workReportService.tryCreateLastStepStockIn(task.getWorkOrderId(), task.getStepNo());
            } catch (Exception e) {
                log.error("返修完成后评估入库失败: workOrderId={}", task.getWorkOrderId(), e);
            }
        }
        return task.getWorkOrderId();
    }
}

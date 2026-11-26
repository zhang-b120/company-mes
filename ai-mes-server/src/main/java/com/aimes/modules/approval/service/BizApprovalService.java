package com.aimes.modules.approval.service;

import com.aimes.common.event.ApprovalCompletedEvent;
import com.aimes.common.event.ApprovalRejectedEvent;
import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.approval.entity.*;
import com.aimes.modules.approval.mapper.*;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.entity.SysUserRole;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizApprovalService extends ServiceImpl<BizApprovalInstanceMapper, BizApprovalInstance> {

    private final BizApprovalFlowMapper flowMapper;
    private final BizApprovalNodeMapper nodeMapper;
    private final BizApprovalNodeApproverMapper approverMapper;
    private final BizApprovalRecordMapper recordMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final BizSalesOrderMapper salesOrderMapper;
    private final com.aimes.modules.bom.mapper.BizBomVersionMapper bomVersionMapper;
    private final com.aimes.modules.master.mapper.BizMaterialMapper materialMapper;
    private final com.aimes.modules.order.mapper.BizOrderChangeMapper orderChangeMapper;
    private final com.aimes.modules.quality.mapper.BizQcDisposalMapper qcDisposalMapper;

    /**
     * 发起审批
     * @return 审批实例ID，若无可用流程返回null
     */
    @Transactional
    public Long startApproval(String businessType, Long businessId, Long applicantId) {
        // 查找匹配的启用流程
        BizApprovalFlow flow = flowMapper.selectOne(
                new LambdaQueryWrapper<BizApprovalFlow>()
                        .eq(BizApprovalFlow::getBusinessType, businessType)
                        .eq(BizApprovalFlow::getEnabled, 1)
                        .last("LIMIT 1"));
        if (flow == null) return null;

        // 获取首个节点
        BizApprovalNode firstNode = nodeMapper.selectOne(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getFlowId, flow.getId())
                        .orderByAsc(BizApprovalNode::getNodeOrder)
                        .last("LIMIT 1"));
        if (firstNode == null) return null;

        // 创建审批实例
        BizApprovalInstance instance = new BizApprovalInstance();
        instance.setFlowId(flow.getId());
        instance.setBusinessType(businessType);
        instance.setBusinessId(businessId);
        instance.setStatus("PENDING");
        instance.setCurrentNodeId(firstNode.getId());
        instance.setApplicantId(applicantId);
        save(instance);

        return instance.getId();
    }

    /**
     * 审批通过
     * 并行审批: 同一 nodeOrder 下的多个 node 视为并行, 当前 order 组所有节点都满足完成条件后才推进到下一个 order
     */
    @Transactional
    public void approve(Long instanceId, Long approverId, String remark) {
        BizApprovalInstance instance = getById(instanceId);
        if (instance == null) throw new BusinessException("审批实例不存在");
        if (!"PENDING".equals(instance.getStatus())) throw new BusinessException("当前审批状态不允许操作");

        // 在当前 order 组里找到该用户对应的节点
        Long matchedNodeId = findApproverNodeId(instance, approverId);
        if (matchedNodeId == null) throw new BusinessException("当前用户无审批权限");

        // 记录审批 (打到具体匹配的节点上)
        BizApprovalRecord record = new BizApprovalRecord();
        record.setInstanceId(instanceId);
        record.setNodeId(matchedNodeId);
        record.setApproverId(approverId);
        record.setAction("APPROVE");
        record.setRemark(remark);
        record.setActionTime(LocalDateTime.now());
        recordMapper.insert(record);

        // 检查当前 order 组是否全部完成 → 推进到下一个 order
        List<BizApprovalNode> currentOrderNodes = getCurrentOrderNodes(instance);
        boolean allDone = !currentOrderNodes.isEmpty()
                && currentOrderNodes.stream().allMatch(n -> isNodeComplete(n, instanceId));
        if (allDone) {
            advanceToNextOrderGroup(instance, currentOrderNodes.get(0), approverId, remark);
        }
    }

    /**
     * 审批驳回
     */
    @Transactional
    public void reject(Long instanceId, Long approverId, String remark) {
        BizApprovalInstance instance = getById(instanceId);
        if (instance == null) throw new BusinessException("审批实例不存在");
        if (!"PENDING".equals(instance.getStatus())) throw new BusinessException("当前审批状态不允许操作");

        Long matchedNodeId = findApproverNodeId(instance, approverId);
        if (matchedNodeId == null) throw new BusinessException("当前用户无审批权限");

        BizApprovalRecord record = new BizApprovalRecord();
        record.setInstanceId(instanceId);
        record.setNodeId(matchedNodeId);
        record.setApproverId(approverId);
        record.setAction("REJECT");
        record.setRemark(remark);
        record.setActionTime(LocalDateTime.now());
        recordMapper.insert(record);

        // 驳回直接终止
        instance.setStatus("REJECTED");
        updateById(instance);

        // 发布驳回事件，驱动业务单据状态回退
        eventPublisher.publishEvent(
                new ApprovalRejectedEvent(this, instance.getBusinessType(), instance.getBusinessId(), approverId, remark));
    }

    /**
     * 申请人撤回
     */
    @Transactional
    public void cancel(Long instanceId, Long userId) {
        BizApprovalInstance instance = getById(instanceId);
        if (instance == null) throw new BusinessException("审批实例不存在");
        if (!"PENDING".equals(instance.getStatus())) throw new BusinessException("当前审批状态不允许撤回");
        if (!instance.getApplicantId().equals(userId)) throw new BusinessException("只有申请人才能撤回");

        instance.setStatus("CANCELLED");
        updateById(instance);
    }

    /**
     * 获取审批进度
     */
    public Map<String, Object> getApprovalProgress(String businessType, Long businessId) {
        BizApprovalInstance instance = getOne(
                new LambdaQueryWrapper<BizApprovalInstance>()
                        .eq(BizApprovalInstance::getBusinessType, businessType)
                        .eq(BizApprovalInstance::getBusinessId, businessId)
                        .orderByDesc(BizApprovalInstance::getCreateTime)
                        .last("LIMIT 1"));
        if (instance == null) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instance.getId());
        result.put("status", instance.getStatus());
        result.put("applicantId", instance.getApplicantId());

        // 申请人名称
        SysUser applicant = userMapper.selectById(instance.getApplicantId());
        result.put("applicantName", applicant != null ? applicant.getRealName() : "");

        // 流程信息
        BizApprovalFlow flow = flowMapper.selectById(instance.getFlowId());
        result.put("flowName", flow != null ? flow.getFlowName() : "");

        // 所有节点
        List<BizApprovalNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getFlowId, instance.getFlowId())
                        .orderByAsc(BizApprovalNode::getNodeOrder));

        // 所有审批记录
        List<BizApprovalRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<BizApprovalRecord>()
                        .eq(BizApprovalRecord::getInstanceId, instance.getId())
                        .orderByAsc(BizApprovalRecord::getActionTime));

        // 批量查询审批人名称
        Set<Long> approverIds = records.stream().map(BizApprovalRecord::getApproverId).collect(Collectors.toSet());
        Map<Long, String> userNameMap = new HashMap<>();
        if (!approverIds.isEmpty()) {
            List<SysUser> users = userMapper.selectBatchIds(approverIds);
            users.forEach(u -> userNameMap.put(u.getId(), u.getRealName()));
        }

        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (BizApprovalNode node : nodes) {
            Map<String, Object> nodeMap = new LinkedHashMap<>();
            nodeMap.put("nodeId", node.getId());
            nodeMap.put("nodeName", node.getNodeName());
            nodeMap.put("nodeOrder", node.getNodeOrder());
            nodeMap.put("approvalMode", node.getApprovalMode());

            // 该节点的审批记录
            List<Map<String, Object>> nodeRecords = records.stream()
                    .filter(r -> r.getNodeId().equals(node.getId()))
                    .map(r -> {
                        Map<String, Object> rm = new LinkedHashMap<>();
                        rm.put("approverId", r.getApproverId());
                        rm.put("approverName", userNameMap.getOrDefault(r.getApproverId(), ""));
                        rm.put("action", r.getAction());
                        rm.put("remark", r.getRemark());
                        rm.put("actionTime", r.getActionTime());
                        return rm;
                    }).collect(Collectors.toList());
            nodeMap.put("records", nodeRecords);

            // 节点状态(并行: 同 nodeOrder 视为同 order 组)
            BizApprovalNode currentNodeRef = instance.getCurrentNodeId() != null
                    ? nodes.stream().filter(n -> n.getId().equals(instance.getCurrentNodeId())).findFirst().orElse(null)
                    : null;
            boolean isCurrentOrder = currentNodeRef != null
                    && Objects.equals(currentNodeRef.getNodeOrder(), node.getNodeOrder());
            boolean hasPassed = records.stream().anyMatch(r -> r.getNodeId().equals(node.getId()) && "APPROVE".equals(r.getAction()));
            boolean hasRejected = records.stream().anyMatch(r -> r.getNodeId().equals(node.getId()) && "REJECT".equals(r.getAction()));
            // 该节点是否完成(单节点会签/或签完成判断)
            boolean nodeComplete = hasPassed && isNodeComplete(node, instance.getId());

            if (hasRejected) nodeMap.put("nodeStatus", "rejected");
            else if (nodeComplete && !isCurrentOrder) nodeMap.put("nodeStatus", "approved");
            else if (nodeComplete && isCurrentOrder) nodeMap.put("nodeStatus", "approved"); // 并行: 自身完成但等其他并行节点
            else if (isCurrentOrder && "PENDING".equals(instance.getStatus())) nodeMap.put("nodeStatus", "pending");
            else nodeMap.put("nodeStatus", "waiting");

            // 审批人信息
            List<BizApprovalNodeApprover> nodeApprovers = approverMapper.selectList(
                    new LambdaQueryWrapper<BizApprovalNodeApprover>().eq(BizApprovalNodeApprover::getNodeId, node.getId()));
            nodeMap.put("approvers", nodeApprovers);

            nodeList.add(nodeMap);
        }
        result.put("nodes", nodeList);
        result.put("createTime", instance.getCreateTime());

        return result;
    }

    /**
     * 获取待我审批的任务（列表，兼容旧接口）
     */
    public List<Map<String, Object>> getMyPendingTasks(Long userId) {
        return getMyPendingTasksInternal(userId);
    }

    /**
     * 解析指定审批实例的"当前节点"所有审批人用户 ID(用于发通知)
     * user 类型直接返回;role 类型展开为该角色下的所有用户
     */
    public java.util.Set<Long> resolveCurrentApproverUserIds(Long instanceId) {
        BizApprovalInstance instance = getById(instanceId);
        if (instance == null || instance.getCurrentNodeId() == null) return java.util.Set.of();
        // 并行: 取当前 order 组所有节点的审批人合集
        List<BizApprovalNode> currentOrderNodes = getCurrentOrderNodes(instance);
        if (currentOrderNodes.isEmpty()) return java.util.Set.of();
        List<Long> nodeIds = currentOrderNodes.stream().map(BizApprovalNode::getId).collect(Collectors.toList());
        List<BizApprovalNodeApprover> approvers = approverMapper.selectList(
                new LambdaQueryWrapper<BizApprovalNodeApprover>()
                        .in(BizApprovalNodeApprover::getNodeId, nodeIds));
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        for (BizApprovalNodeApprover a : approvers) {
            if ("user".equalsIgnoreCase(a.getApproverType())) {
                if (a.getApproverId() != null) userIds.add(a.getApproverId());
            } else if ("role".equalsIgnoreCase(a.getApproverType())) {
                if (a.getApproverId() == null) continue;
                List<SysUserRole> roleUsers = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, a.getApproverId()));
                roleUsers.forEach(ur -> userIds.add(ur.getUserId()));
            }
        }
        return userIds;
    }

    /**
     * 获取我的审批分页（待我审批 / 我发起的 / 审批记录）
     */
    public PageResult<Map<String, Object>> getMyApprovalPage(Long userId, String tab, int pageNum, int pageSize) {
        List<Map<String, Object>> allTasks;
        if ("mine".equals(tab)) {
            // 我发起的审批
            List<BizApprovalInstance> instances = list(
                    new LambdaQueryWrapper<BizApprovalInstance>()
                            .eq(BizApprovalInstance::getApplicantId, userId)
                            .orderByDesc(BizApprovalInstance::getCreateTime));
            allTasks = instances.stream().map(this::buildTaskMap).collect(Collectors.toList());
        } else if ("history".equals(tab)) {
            // 审批记录(我审批过的)
            List<BizApprovalRecord> records = recordMapper.selectList(
                    new LambdaQueryWrapper<BizApprovalRecord>()
                            .eq(BizApprovalRecord::getApproverId, userId)
                            .orderByDesc(BizApprovalRecord::getActionTime));
            allTasks = new ArrayList<>();
            for (BizApprovalRecord rec : records) {
                BizApprovalInstance inst = getById(rec.getInstanceId());
                if (inst == null) continue;
                Map<String, Object> m = buildTaskMap(inst);
                m.put("myAction", rec.getAction());           // APPROVE / REJECT
                m.put("myRemark", rec.getRemark());
                m.put("myActionTime", rec.getActionTime());
                allTasks.add(m);
            }
        } else {
            // 待我审批（默认）
            allTasks = getMyPendingTasksInternal(userId);
        }

        int total = allTasks.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        List<Map<String, Object>> pageData = start < total ? new ArrayList<>(allTasks.subList(start, end)) : Collections.emptyList();
        return new PageResult<>(pageData, (long) total, pageNum, pageSize);
    }

    /**
     * 根据业务查询审批实例
     */
    public BizApprovalInstance getByBusiness(String businessType, Long businessId) {
        return getOne(new LambdaQueryWrapper<BizApprovalInstance>()
                .eq(BizApprovalInstance::getBusinessType, businessType)
                .eq(BizApprovalInstance::getBusinessId, businessId)
                .eq(BizApprovalInstance::getStatus, "PENDING")
                .last("LIMIT 1"));
    }

    // ==================== 内部方法 ====================

    private List<Map<String, Object>> getMyPendingTasksInternal(Long userId) {
        // 查用户角色
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

        // 查所有PENDING实例
        List<BizApprovalInstance> instances = list(
                new LambdaQueryWrapper<BizApprovalInstance>()
                        .eq(BizApprovalInstance::getStatus, "PENDING")
                        .orderByDesc(BizApprovalInstance::getCreateTime));

        List<Map<String, Object>> tasks = new ArrayList<>();
        for (BizApprovalInstance inst : instances) {
            if (inst.getCurrentNodeId() == null) continue;

            // 并行: 在当前 order 组里, 检查任一节点的审批人是否包含该用户
            List<BizApprovalNode> currentOrderNodes = getCurrentOrderNodes(inst);
            if (currentOrderNodes.isEmpty()) continue;

            boolean addable = false;
            for (BizApprovalNode node : currentOrderNodes) {
                List<BizApprovalNodeApprover> approvers = approverMapper.selectList(
                        new LambdaQueryWrapper<BizApprovalNodeApprover>()
                                .eq(BizApprovalNodeApprover::getNodeId, node.getId()));
                boolean isApprover = false;
                for (BizApprovalNodeApprover a : approvers) {
                    if ("user".equals(a.getApproverType()) && a.getApproverId().equals(userId)) {
                        isApprover = true; break;
                    }
                    if ("role".equals(a.getApproverType()) && roleIds.contains(a.getApproverId())) {
                        isApprover = true; break;
                    }
                }
                if (!isApprover) continue;

                // 该用户在此节点已审批过, 跳过(其他并行节点继续看)
                long alreadyApproved = recordMapper.selectCount(
                        new LambdaQueryWrapper<BizApprovalRecord>()
                                .eq(BizApprovalRecord::getInstanceId, inst.getId())
                                .eq(BizApprovalRecord::getNodeId, node.getId())
                                .eq(BizApprovalRecord::getApproverId, userId));
                if (alreadyApproved == 0) { addable = true; break; }
            }
            if (addable) tasks.add(buildTaskMap(inst));
        }

        return tasks;
    }

    private Map<String, Object> buildTaskMap(BizApprovalInstance inst) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("instanceId", inst.getId());
        task.put("businessType", inst.getBusinessType());
        task.put("businessId", inst.getBusinessId());
        task.put("businessNo", getBusinessNo(inst.getBusinessType(), inst.getBusinessId()));
        task.put("status", inst.getStatus());
        task.put("applicantId", inst.getApplicantId());

        SysUser applicant = userMapper.selectById(inst.getApplicantId());
        task.put("applicantName", applicant != null ? applicant.getRealName() : "");

        BizApprovalFlow flow = flowMapper.selectById(inst.getFlowId());
        task.put("flowName", flow != null ? flow.getFlowName() : "");

        if (inst.getCurrentNodeId() != null) {
            BizApprovalNode node = nodeMapper.selectById(inst.getCurrentNodeId());
            task.put("currentNodeName", node != null ? node.getNodeName() : "");
        } else {
            task.put("currentNodeName", "");
        }

        task.put("createTime", inst.getCreateTime());
        return task;
    }

    private String getBusinessNo(String businessType, Long businessId) {
        if (businessId == null) return "";
        if ("sales_order".equals(businessType)) {
            BizSalesOrder order = salesOrderMapper.selectById(businessId);
            return order != null ? order.getOrderNo() : "";
        }
        if ("order_change".equals(businessType)) {
            // 订单变更: 显示"订单号 (变更类型中文)"
            try {
                com.aimes.modules.order.entity.BizOrderChange ch = orderChangeMapper.selectById(businessId);
                if (ch == null) return "";
                BizSalesOrder order = ch.getOrderId() != null ? salesOrderMapper.selectById(ch.getOrderId()) : null;
                String orderNo = order != null ? order.getOrderNo() : "";
                String type = ch.getChangeType() != null ? ch.getChangeType() : "";
                return orderNo + (type.isEmpty() ? ""
                        : " (" + com.aimes.modules.order.service.BizOrderChangeService.changeTypeLabel(type) + ")");
            } catch (Exception e) {
                return "OC-" + businessId;
            }
        }
        if ("bom_version_activation".equals(businessType)) {
            // BOM 审批显示"产品名 v版本号"
            try {
                com.aimes.modules.bom.entity.BizBomVersion bv = bomVersionMapper.selectById(businessId);
                if (bv == null) return "";
                String productName = "";
                if (bv.getProductId() != null) {
                    com.aimes.modules.master.entity.BizMaterial mat = materialMapper.selectById(bv.getProductId());
                    if (mat != null) productName = mat.getMaterialName();
                }
                String ver = bv.getVersionNo() != null ? bv.getVersionNo() : ("v" + businessId);
                return productName.isEmpty() ? ver : (productName + " " + ver);
            } catch (Exception ignore) {
                return "BOM-" + businessId;
            }
        }
        if ("qc_concession".equals(businessType)) {
            // 质检让步接收: 显示处置单号
            try {
                com.aimes.modules.quality.entity.BizQcDisposal d = qcDisposalMapper.selectById(businessId);
                return d != null ? d.getDisposalNo() : "";
            } catch (Exception ignore) {
                return "QD-" + businessId;
            }
        }
        return "";
    }

    /** 取实例当前 order 组的所有节点 (相同 nodeOrder = 并行) */
    private List<BizApprovalNode> getCurrentOrderNodes(BizApprovalInstance instance) {
        if (instance.getCurrentNodeId() == null) return Collections.emptyList();
        BizApprovalNode currentNode = nodeMapper.selectById(instance.getCurrentNodeId());
        if (currentNode == null) return Collections.emptyList();
        return nodeMapper.selectList(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getFlowId, instance.getFlowId())
                        .eq(BizApprovalNode::getNodeOrder, currentNode.getNodeOrder()));
    }

    /**
     * 在当前 order 组里找用户应该签批的节点; 返回 null 表示无权限或已签批过
     * 一个用户被多个并行节点都关联时, 取第一个尚未由该用户通过的节点
     */
    private Long findApproverNodeId(BizApprovalInstance instance, Long approverId) {
        List<BizApprovalNode> currentOrderNodes = getCurrentOrderNodes(instance);
        if (currentOrderNodes.isEmpty()) return null;
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, approverId));
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

        for (BizApprovalNode node : currentOrderNodes) {
            // 已经在该节点通过过, 跳过(避免一人多签)
            long alreadyApproved = recordMapper.selectCount(
                    new LambdaQueryWrapper<BizApprovalRecord>()
                            .eq(BizApprovalRecord::getInstanceId, instance.getId())
                            .eq(BizApprovalRecord::getNodeId, node.getId())
                            .eq(BizApprovalRecord::getApproverId, approverId)
                            .eq(BizApprovalRecord::getAction, "APPROVE"));
            if (alreadyApproved > 0) continue;

            List<BizApprovalNodeApprover> approvers = approverMapper.selectList(
                    new LambdaQueryWrapper<BizApprovalNodeApprover>().eq(BizApprovalNodeApprover::getNodeId, node.getId()));
            for (BizApprovalNodeApprover a : approvers) {
                if ("user".equals(a.getApproverType()) && a.getApproverId().equals(approverId)) return node.getId();
                if ("role".equals(a.getApproverType()) && roleIds.contains(a.getApproverId())) return node.getId();
            }
        }
        return null;
    }

    private boolean isNodeComplete(BizApprovalNode node, Long instanceId) {
        if ("OR".equals(node.getApprovalMode())) {
            // 或签: 只要有一条通过记录即完成
            return recordMapper.selectCount(
                    new LambdaQueryWrapper<BizApprovalRecord>()
                            .eq(BizApprovalRecord::getInstanceId, instanceId)
                            .eq(BizApprovalRecord::getNodeId, node.getId())
                            .eq(BizApprovalRecord::getAction, "APPROVE")) > 0;
        } else {
            // 会签: 所有审批人都必须通过
            List<BizApprovalNodeApprover> approvers = approverMapper.selectList(
                    new LambdaQueryWrapper<BizApprovalNodeApprover>().eq(BizApprovalNodeApprover::getNodeId, node.getId()));

            // 收集所有需要审批的用户ID
            Set<Long> requiredApproverIds = new HashSet<>();
            for (BizApprovalNodeApprover a : approvers) {
                if ("user".equals(a.getApproverType())) {
                    requiredApproverIds.add(a.getApproverId());
                } else if ("role".equals(a.getApproverType())) {
                    // 查该角色下的所有用户(简化处理，只需检查审批记录数>=审批人数)
                    List<SysUserRole> roleUsers = userRoleMapper.selectList(
                            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, a.getApproverId()));
                    roleUsers.forEach(ur -> requiredApproverIds.add(ur.getUserId()));
                }
            }

            long approvedCount = recordMapper.selectCount(
                    new LambdaQueryWrapper<BizApprovalRecord>()
                            .eq(BizApprovalRecord::getInstanceId, instanceId)
                            .eq(BizApprovalRecord::getNodeId, node.getId())
                            .eq(BizApprovalRecord::getAction, "APPROVE"));

            return approvedCount >= requiredApproverIds.size();
        }
    }

    /** 推进到下一个 order 组(下一组并行节点); 没有则审批完成 */
    private void advanceToNextOrderGroup(BizApprovalInstance instance, BizApprovalNode currentNode,
                                          Long approverId, String remark) {
        BizApprovalNode nextNode = nodeMapper.selectOne(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getFlowId, instance.getFlowId())
                        .gt(BizApprovalNode::getNodeOrder, currentNode.getNodeOrder())
                        .orderByAsc(BizApprovalNode::getNodeOrder)
                        .last("LIMIT 1"));

        if (nextNode != null) {
            instance.setCurrentNodeId(nextNode.getId());
            updateById(instance);
        } else {
            instance.setStatus("APPROVED");
            instance.setCurrentNodeId(null);
            updateById(instance);
            eventPublisher.publishEvent(
                    new ApprovalCompletedEvent(this, instance.getBusinessType(), instance.getBusinessId(), approverId, remark));
        }
    }
}

package com.aimes.modules.approval.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.approval.entity.BizApprovalFlow;
import com.aimes.modules.approval.entity.BizApprovalNode;
import com.aimes.modules.approval.entity.BizApprovalNodeApprover;
import com.aimes.modules.approval.mapper.BizApprovalFlowMapper;
import com.aimes.modules.approval.mapper.BizApprovalNodeApproverMapper;
import com.aimes.modules.approval.mapper.BizApprovalNodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizApprovalFlowService extends ServiceImpl<BizApprovalFlowMapper, BizApprovalFlow> {

    private final BizApprovalNodeMapper nodeMapper;
    private final BizApprovalNodeApproverMapper approverMapper;

    public PageResult<Map<String, Object>> getPage(String keyword, String businessType, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizApprovalFlow> wrapper = new LambdaQueryWrapper<BizApprovalFlow>()
                .like(StringUtils.hasText(keyword), BizApprovalFlow::getFlowName, keyword)
                .eq(StringUtils.hasText(businessType), BizApprovalFlow::getBusinessType, businessType)
                .orderByDesc(BizApprovalFlow::getCreateTime);
        Page<BizApprovalFlow> page = page(new Page<>(pageNum, pageSize), wrapper);

        List<Map<String, Object>> list = page.getRecords().stream().map(f -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", f.getId());
            map.put("flowName", f.getFlowName());
            map.put("flowCode", f.getFlowCode());
            map.put("businessType", f.getBusinessType());
            map.put("enabled", f.getEnabled());
            map.put("remark", f.getRemark());
            // 查节点数
            long nodeCount = nodeMapper.selectCount(
                    new LambdaQueryWrapper<BizApprovalNode>().eq(BizApprovalNode::getFlowId, f.getId()));
            map.put("nodeCount", nodeCount);
            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    public Map<String, Object> getDetail(Long id) {
        BizApprovalFlow flow = getById(id);
        if (flow == null) throw new BusinessException("审批流程不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", flow.getId());
        result.put("flowName", flow.getFlowName());
        result.put("flowCode", flow.getFlowCode());
        result.put("businessType", flow.getBusinessType());
        result.put("enabled", flow.getEnabled());
        result.put("remark", flow.getRemark());

        // 查询节点列表
        List<BizApprovalNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<BizApprovalNode>()
                        .eq(BizApprovalNode::getFlowId, id)
                        .orderByAsc(BizApprovalNode::getNodeOrder));

        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (BizApprovalNode node : nodes) {
            Map<String, Object> nodeMap = new LinkedHashMap<>();
            nodeMap.put("id", node.getId());
            nodeMap.put("nodeName", node.getNodeName());
            nodeMap.put("nodeOrder", node.getNodeOrder());
            nodeMap.put("approvalMode", node.getApprovalMode());
            nodeMap.put("remark", node.getRemark());

            // 查询审批人
            List<BizApprovalNodeApprover> approvers = approverMapper.selectList(
                    new LambdaQueryWrapper<BizApprovalNodeApprover>()
                            .eq(BizApprovalNodeApprover::getNodeId, node.getId()));
            nodeMap.put("approvers", approvers);
            nodeList.add(nodeMap);
        }
        result.put("nodes", nodeList);

        return result;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void createFlow(Map<String, Object> body) {
        // 唯一性校验
        String flowCode = (String) body.get("flowCode");
        long count = count(new LambdaQueryWrapper<BizApprovalFlow>().eq(BizApprovalFlow::getFlowCode, flowCode));
        if (count > 0) throw new BusinessException("流程编码已存在");

        BizApprovalFlow flow = new BizApprovalFlow();
        flow.setFlowName((String) body.get("flowName"));
        flow.setFlowCode(flowCode);
        flow.setBusinessType((String) body.get("businessType"));
        flow.setEnabled(body.get("enabled") != null ? Integer.valueOf(body.get("enabled").toString()) : 1);
        flow.setRemark((String) body.get("remark"));
        save(flow);

        // 保存节点
        saveNodes(flow.getId(), (List<Map<String, Object>>) body.get("nodes"));
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void updateFlow(Long id, Map<String, Object> body) {
        BizApprovalFlow flow = getById(id);
        if (flow == null) throw new BusinessException("审批流程不存在");

        flow.setFlowName((String) body.get("flowName"));
        flow.setBusinessType((String) body.get("businessType"));
        flow.setEnabled(body.get("enabled") != null ? Integer.valueOf(body.get("enabled").toString()) : 1);
        flow.setRemark((String) body.get("remark"));
        updateById(flow);

        // 删除旧节点和审批人
        List<BizApprovalNode> oldNodes = nodeMapper.selectList(
                new LambdaQueryWrapper<BizApprovalNode>().eq(BizApprovalNode::getFlowId, id));
        for (BizApprovalNode node : oldNodes) {
            approverMapper.delete(new LambdaQueryWrapper<BizApprovalNodeApprover>()
                    .eq(BizApprovalNodeApprover::getNodeId, node.getId()));
        }
        nodeMapper.delete(new LambdaQueryWrapper<BizApprovalNode>().eq(BizApprovalNode::getFlowId, id));

        // 重建节点
        saveNodes(id, (List<Map<String, Object>>) body.get("nodes"));
    }

    @Transactional
    public void deleteFlow(Long id) {
        BizApprovalFlow flow = getById(id);
        if (flow == null) throw new BusinessException("审批流程不存在");

        // 删除节点和审批人
        List<BizApprovalNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<BizApprovalNode>().eq(BizApprovalNode::getFlowId, id));
        for (BizApprovalNode node : nodes) {
            approverMapper.delete(new LambdaQueryWrapper<BizApprovalNodeApprover>()
                    .eq(BizApprovalNodeApprover::getNodeId, node.getId()));
        }
        nodeMapper.delete(new LambdaQueryWrapper<BizApprovalNode>().eq(BizApprovalNode::getFlowId, id));
        removeById(id);
    }

    public void toggleEnabled(Long id) {
        BizApprovalFlow flow = getById(id);
        if (flow == null) throw new BusinessException("审批流程不存在");
        flow.setEnabled(flow.getEnabled() == 1 ? 0 : 1);
        updateById(flow);
    }

    @SuppressWarnings("unchecked")
    private void saveNodes(Long flowId, List<Map<String, Object>> nodesMaps) {
        if (nodesMaps == null || nodesMaps.isEmpty()) return;
        int order = 1;
        for (Map<String, Object> nm : nodesMaps) {
            BizApprovalNode node = new BizApprovalNode();
            node.setFlowId(flowId);
            node.setNodeName((String) nm.get("nodeName"));
            node.setNodeOrder(nm.get("nodeOrder") != null ? Integer.valueOf(nm.get("nodeOrder").toString()) : order);
            node.setApprovalMode(nm.get("approvalMode") != null ? nm.get("approvalMode").toString() : "OR");
            node.setRemark((String) nm.get("remark"));
            nodeMapper.insert(node);

            // 保存审批人
            List<Map<String, Object>> approvers = (List<Map<String, Object>>) nm.get("approvers");
            if (approvers != null) {
                for (Map<String, Object> am : approvers) {
                    BizApprovalNodeApprover approver = new BizApprovalNodeApprover();
                    approver.setNodeId(node.getId());
                    approver.setApproverType(am.get("approverType") != null ? am.get("approverType").toString() : "user");
                    approver.setApproverId(Long.valueOf(am.get("approverId").toString()));
                    approverMapper.insert(approver);
                }
            }
            order++;
        }
    }
}

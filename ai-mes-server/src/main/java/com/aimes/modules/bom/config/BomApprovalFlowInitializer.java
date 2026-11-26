package com.aimes.modules.bom.config;

import com.aimes.modules.approval.entity.BizApprovalFlow;
import com.aimes.modules.approval.entity.BizApprovalNode;
import com.aimes.modules.approval.entity.BizApprovalNodeApprover;
import com.aimes.modules.approval.mapper.BizApprovalFlowMapper;
import com.aimes.modules.approval.mapper.BizApprovalNodeApproverMapper;
import com.aimes.modules.approval.mapper.BizApprovalNodeMapper;
import com.aimes.modules.system.entity.SysRole;
import com.aimes.modules.system.mapper.SysRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启动时初始化 BOM 激活默认审批流(如果不存在)
 * 流程:单节点 → 由 admin 角色审批
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BomApprovalFlowInitializer {

    private static final String BUSINESS_TYPE = "bom_version_activation";
    private static final String FLOW_CODE = "FLOW_BOM_ACTIVATE";

    private final BizApprovalFlowMapper flowMapper;
    private final BizApprovalNodeMapper nodeMapper;
    private final BizApprovalNodeApproverMapper approverMapper;
    private final SysRoleMapper roleMapper;

    @Bean
    public ApplicationRunner initBomApprovalFlow() {
        return args -> {
            try {
                BizApprovalFlow existing = flowMapper.selectOne(
                        new LambdaQueryWrapper<BizApprovalFlow>()
                                .eq(BizApprovalFlow::getFlowCode, FLOW_CODE)
                                .last("LIMIT 1"));
                if (existing != null) {
                    log.info("BOM 激活审批流已存在 (id={})", existing.getId());
                    return;
                }

                BizApprovalFlow flow = new BizApprovalFlow();
                flow.setFlowName("BOM 版本激活审批");
                flow.setFlowCode(FLOW_CODE);
                flow.setBusinessType(BUSINESS_TYPE);
                flow.setEnabled(1);
                flow.setRemark("BOM 版本激活时触发,由管理员审批");
                flowMapper.insert(flow);

                BizApprovalNode node = new BizApprovalNode();
                node.setFlowId(flow.getId());
                node.setNodeName("管理员审批");
                node.setNodeOrder(1);
                node.setApprovalMode("OR");
                nodeMapper.insert(node);

                // 找 admin 角色作为审批人
                SysRole adminRole = roleMapper.selectOne(
                        new LambdaQueryWrapper<SysRole>()
                                .eq(SysRole::getRoleCode, "admin")
                                .last("LIMIT 1"));
                if (adminRole != null) {
                    BizApprovalNodeApprover approver = new BizApprovalNodeApprover();
                    approver.setNodeId(node.getId());
                    approver.setApproverType("role");  // 必须小写, validateApprover 里按 "role"/"user" 判断
                    approver.setApproverId(adminRole.getId());
                    approverMapper.insert(approver);
                }

                log.info("初始化 BOM 激活审批流完成 (flow_id={}, node_id={})", flow.getId(), node.getId());
            } catch (Exception e) {
                log.error("初始化 BOM 激活审批流失败", e);
            }
        };
    }
}

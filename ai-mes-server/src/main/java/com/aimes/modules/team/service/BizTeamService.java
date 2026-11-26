package com.aimes.modules.team.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.team.entity.BizTeam;
import com.aimes.modules.team.entity.BizTeamMember;
import com.aimes.modules.team.mapper.BizTeamMapper;
import com.aimes.modules.team.mapper.BizTeamMemberMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizTeamService extends ServiceImpl<BizTeamMapper, BizTeam> {

    private final BizTeamMemberMapper memberMapper;
    private final SysUserMapper userMapper;

    /**
     * 班组列表 - 含组长姓名 + 成员数
     */
    public List<Map<String, Object>> list(String workshop, String shiftType, String keyword) {
        LambdaQueryWrapper<BizTeam> wrapper = new LambdaQueryWrapper<BizTeam>()
                .eq(StringUtils.hasText(workshop), BizTeam::getWorkshop, workshop)
                .eq(StringUtils.hasText(shiftType), BizTeam::getShiftType, shiftType)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(BizTeam::getTeamName, keyword))
                .orderByDesc(BizTeam::getCreateTime);

        List<BizTeam> teams = list(wrapper);

        // 批量查询组长姓名
        Set<Long> leaderIds = teams.stream().map(BizTeam::getLeaderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> leaderNameMap = leaderIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(leaderIds).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        return teams.stream().map(team -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", team.getId());
            map.put("teamName", team.getTeamName());
            map.put("workshop", team.getWorkshop());
            map.put("leaderId", team.getLeaderId());
            map.put("leaderName", team.getLeaderId() != null ? leaderNameMap.get(team.getLeaderId()) : "");
            map.put("shiftType", team.getShiftType());
            map.put("effectiveDate", team.getEffectiveDate());
            map.put("status", team.getStatus());
            map.put("remark", team.getRemark());
            map.put("createTime", team.getCreateTime());

            // 成员数
            long memberCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<BizTeamMember>().eq(BizTeamMember::getTeamId, team.getId()));
            map.put("memberCount", memberCount);

            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 创建班组
     */
    @Transactional
    public void create(BizTeam team) {
        team.setStatus("active");
        save(team);
        // 如果设置了负责人，自动加入成员
        if (team.getLeaderId() != null) {
            addLeaderAsMember(team.getId(), team.getLeaderId());
        }
    }

    /**
     * 更新班组
     */
    @Transactional
    public void update(Long id, BizTeam team) {
        BizTeam existing = getById(id);
        if (existing == null) {
            throw new BusinessException("班组不存在");
        }
        team.setId(id);
        updateById(team);
        // 如果设置了负责人，自动加入成员
        if (team.getLeaderId() != null) {
            addLeaderAsMember(id, team.getLeaderId());
        }
    }

    /**
     * 删除班组
     */
    @Transactional
    public void delete(Long id) {
        BizTeam existing = getById(id);
        if (existing == null) {
            throw new BusinessException("班组不存在");
        }
        removeById(id);
        // 同时删除成员关系
        memberMapper.delete(new LambdaQueryWrapper<BizTeamMember>().eq(BizTeamMember::getTeamId, id));
    }

    /**
     * 获取成员列表（含权重）
     */
    public List<Map<String, Object>> getMembers(Long teamId) {
        BizTeam team = getById(teamId);
        if (team == null) {
            throw new BusinessException("班组不存在");
        }

        List<BizTeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<BizTeamMember>()
                        .eq(BizTeamMember::getTeamId, teamId)
                        .orderByAsc(BizTeamMember::getJoinedAt));

        // 批量查询用户姓名
        Set<Long> userIds = members.stream().map(BizTeamMember::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        Long leaderId = team.getLeaderId();
        List<Map<String, Object>> result = members.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("teamId", m.getTeamId());
            map.put("userId", m.getUserId());
            String name = userNameMap.getOrDefault(m.getUserId(), "");
            // 同时返回 userName(兼容历史调用方) + realName(SysUser 标准字段名,前端 select 优先用)
            map.put("userName", name);
            map.put("realName", name);
            map.put("isLeader", leaderId != null && leaderId.equals(m.getUserId()));
            map.put("weight", m.getWeight());
            map.put("joinedAt", m.getJoinedAt());
            return map;
        }).collect(Collectors.toList());
        // 组长排第一
        result.sort((a, b) -> Boolean.compare((Boolean) b.get("isLeader"), (Boolean) a.get("isLeader")));
        return result;
    }

    /**
     * 添加成员 - 添加后自动均分权重
     */
    @Transactional
    public void addMembers(Long teamId, List<Long> userIds) {
        BizTeam team = getById(teamId);
        if (team == null) {
            throw new BusinessException("班组不存在");
        }

        for (Long userId : userIds) {
            // 检查是否已存在
            long count = memberMapper.selectCount(
                    new LambdaQueryWrapper<BizTeamMember>()
                            .eq(BizTeamMember::getTeamId, teamId)
                            .eq(BizTeamMember::getUserId, userId));
            if (count > 0) {
                continue;
            }

            BizTeamMember member = new BizTeamMember();
            member.setTeamId(teamId);
            member.setUserId(userId);
            member.setWeight(BigDecimal.ZERO);
            member.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(member);
        }

        // 自动均分权重
        equalizeWeights(teamId);
    }

    /**
     * 移除成员 - 移除后权重按比例重新分配
     */
    @Transactional
    public void removeMember(Long teamId, Long memberId) {
        BizTeamMember member = memberMapper.selectById(memberId);
        if (member == null || !member.getTeamId().equals(teamId)) {
            throw new BusinessException("成员不存在");
        }

        BigDecimal removedWeight = member.getWeight();
        memberMapper.deleteById(memberId);

        // 剩余成员按比例重新分配
        List<BizTeamMember> remaining = memberMapper.selectList(
                new LambdaQueryWrapper<BizTeamMember>().eq(BizTeamMember::getTeamId, teamId));

        if (remaining.isEmpty()) {
            return;
        }

        BigDecimal totalRemainingWeight = remaining.stream()
                .map(BizTeamMember::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalRemainingWeight.compareTo(BigDecimal.ZERO) == 0) {
            // 如果剩余权重为0，均分
            equalizeWeights(teamId);
        } else {
            // 按比例分配被移除成员的权重
            for (BizTeamMember m : remaining) {
                BigDecimal ratio = m.getWeight().divide(totalRemainingWeight, 10, RoundingMode.HALF_UP);
                BigDecimal newWeight = m.getWeight().add(removedWeight.multiply(ratio));
                m.setWeight(newWeight.setScale(4, RoundingMode.HALF_UP));
                memberMapper.updateById(m);
            }

            // 修正最后一个成员的权重确保总和为1
            normalizeWeights(teamId);
        }
    }

    /**
     * 保存权重 - 校验总和=1.0
     */
    @Transactional
    public void saveWeights(Long teamId, List<Map<String, Object>> weightList) {
        BizTeam team = getById(teamId);
        if (team == null) {
            throw new BusinessException("班组不存在");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : weightList) {
            BigDecimal weight = new BigDecimal(item.get("weight").toString());
            total = total.add(weight);
        }

        if (total.setScale(2, RoundingMode.HALF_UP).compareTo(new BigDecimal("1.00")) != 0) {
            throw new BusinessException("权重总和必须等于1.0");
        }

        for (Map<String, Object> item : weightList) {
            Long memberId = Long.valueOf(item.get("memberId").toString());
            BigDecimal weight = new BigDecimal(item.get("weight").toString());

            BizTeamMember member = memberMapper.selectById(memberId);
            if (member == null || !member.getTeamId().equals(teamId)) {
                throw new BusinessException("成员不属于该班组");
            }
            member.setWeight(weight);
            memberMapper.updateById(member);
        }
    }

    /**
     * 一键均分权重
     */
    @Transactional
    public void equalizeWeights(Long teamId) {
        List<BizTeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<BizTeamMember>().eq(BizTeamMember::getTeamId, teamId));

        if (members.isEmpty()) {
            return;
        }

        int size = members.size();
        BigDecimal avgWeight = BigDecimal.ONE.divide(new BigDecimal(size), 4, RoundingMode.HALF_UP);

        for (int i = 0; i < members.size(); i++) {
            BizTeamMember m = members.get(i);
            if (i == members.size() - 1) {
                // 最后一个成员补偿差值，确保总和=1
                BigDecimal allocated = avgWeight.multiply(new BigDecimal(size - 1));
                m.setWeight(BigDecimal.ONE.subtract(allocated));
            } else {
                m.setWeight(avgWeight);
            }
            memberMapper.updateById(m);
        }
    }

    /**
     * 将负责人自动加入班组成员（如果尚未加入）
     */
    private void addLeaderAsMember(Long teamId, Long leaderId) {
        long count = memberMapper.selectCount(
                new LambdaQueryWrapper<BizTeamMember>()
                        .eq(BizTeamMember::getTeamId, teamId)
                        .eq(BizTeamMember::getUserId, leaderId));
        if (count == 0) {
            BizTeamMember member = new BizTeamMember();
            member.setTeamId(teamId);
            member.setUserId(leaderId);
            member.setWeight(BigDecimal.ZERO);
            member.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(member);
            // 重新均分权重
            equalizeWeights(teamId);
        }
    }

    /**
     * 归一化权重，确保总和精确等于1
     */
    private void normalizeWeights(Long teamId) {
        List<BizTeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<BizTeamMember>().eq(BizTeamMember::getTeamId, teamId));

        if (members.isEmpty()) {
            return;
        }

        BigDecimal total = members.stream()
                .map(BizTeamMember::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal diff = BigDecimal.ONE.subtract(total);
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            BizTeamMember last = members.get(members.size() - 1);
            last.setWeight(last.getWeight().add(diff));
            memberMapper.updateById(last);
        }
    }
}

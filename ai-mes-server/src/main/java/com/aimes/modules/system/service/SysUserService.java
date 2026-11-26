package com.aimes.modules.system.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.entity.SysUserRole;
import com.aimes.modules.system.dto.SysUserDTO;
import com.aimes.modules.system.mapper.SysDeptMapper;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.mapper.SysUserRoleMapper;
import com.aimes.modules.team.entity.BizTeam;
import com.aimes.modules.team.entity.BizTeamMember;
import com.aimes.modules.team.mapper.BizTeamMapper;
import com.aimes.modules.team.mapper.BizTeamMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> {

    private final SysUserRoleMapper userRoleMapper;
    private final BizTeamMemberMapper teamMemberMapper;
    private final BizTeamMapper teamMapper;
    private final SysDeptMapper deptMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final com.aimes.common.cache.NameCacheEvictor nameCacheEvictor;

    public PageResult<Map<String, Object>> getPage(String realName, String phone, String username, Long roleId, String status, int pageNum, int pageSize) {
        List<Long> filterUserIds = null;
        if (roleId != null) {
            filterUserIds = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId))
                    .stream().map(SysUserRole::getUserId).collect(Collectors.toList());
            if (filterUserIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, pageNum, pageSize);
            }
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(realName), SysUser::getRealName, realName)
                .like(StringUtils.hasText(phone), SysUser::getPhone, phone)
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .eq(StringUtils.hasText(status), SysUser::getStatus, status)
                .in(filterUserIds != null, SysUser::getId, filterUserIds)
                .orderByDesc(SysUser::getCreateTime);

        Page<SysUser> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 批量查询部门名称
        Set<Long> deptIds = page.getRecords().stream().map(SysUser::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            deptMapper.selectBatchIds(deptIds).forEach(d -> deptNameMap.put(d.getId(), d.getDeptName()));
        }

        // 批量查询用户所属班组名称 + 班组ID列表
        Set<Long> pageUserIds = page.getRecords().stream().map(SysUser::getId).collect(Collectors.toSet());
        Map<Long, String> userTeamNameMap = new HashMap<>();
        Map<Long, List<Long>> userTeamIdsMap = new HashMap<>();
        if (!pageUserIds.isEmpty()) {
            List<BizTeamMember> memberList = teamMemberMapper.selectList(
                    new LambdaQueryWrapper<BizTeamMember>().in(BizTeamMember::getUserId, pageUserIds));
            Set<Long> teamIds = memberList.stream().map(BizTeamMember::getTeamId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!teamIds.isEmpty()) {
                Map<Long, String> teamNameMap = teamMapper.selectBatchIds(teamIds).stream()
                        .collect(Collectors.toMap(BizTeam::getId, BizTeam::getTeamName, (a, b) -> a));
                memberList.stream().collect(Collectors.groupingBy(BizTeamMember::getUserId)).forEach((uid, members) -> {
                    List<Long> ids = members.stream().map(BizTeamMember::getTeamId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
                    String names = ids.stream()
                            .map(tid -> teamNameMap.getOrDefault(tid, ""))
                            .filter(n -> !n.isEmpty())
                            .collect(Collectors.joining(","));
                    userTeamIdsMap.put(uid, ids);
                    userTeamNameMap.put(uid, names);
                });
            }
        }

        List<Map<String, Object>> list = page.getRecords().stream().map(u -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("realName", u.getRealName());
            map.put("phone", u.getPhone());
            map.put("icCardNo", u.getIcCardNo());
            map.put("roleNames", baseMapper.selectRoleNames(u.getId()));
            map.put("roleIds", baseMapper.selectRoleIds(u.getId()));
            map.put("deptId", u.getDeptId());
            map.put("deptName", deptNameMap.getOrDefault(u.getDeptId(), ""));
            map.put("teamIds", userTeamIdsMap.getOrDefault(u.getId(), Collections.emptyList()));
            map.put("teamName", userTeamNameMap.getOrDefault(u.getId(), ""));
            map.put("status", u.getStatus());
            map.put("hireDate", u.getHireDate());
            map.put("createTime", u.getCreateTime());
            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    @Transactional
    public void createUser(SysUserDTO dto) {
        long count = count(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (count > 0) throw new BusinessException("账号已存在");

        String icCardNo = StringUtils.hasText(dto.getIcCardNo()) ? dto.getIcCardNo().trim() : null;
        if (icCardNo != null && count(new LambdaQueryWrapper<SysUser>().eq(SysUser::getIcCardNo, icCardNo)) > 0) {
            throw new BusinessException("IC卡号已被绑定: " + icCardNo);
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setIcCardNo(icCardNo);
        user.setPassword(passwordEncoder.encode(dto.getPassword() != null ? dto.getPassword() : "123456"));
        user.setDeptId(dto.getDeptId());
        user.setHireDate(dto.getHireDate());
        user.setRemark(dto.getRemark());
        user.setStatus("active");
        save(user);

        saveUserRoles(user.getId(), dto.getRoleIds());
        saveUserTeams(user.getId(), dto.getTeamIds());
    }

    @Transactional
    public void updateUser(Long id, SysUserDTO dto) {
        SysUser user = getById(id);
        if (user == null) throw new BusinessException("用户不存在");
        if (dto.getUsername() != null && !dto.getUsername().equals(user.getUsername())) {
            long count = count(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, dto.getUsername()).ne(SysUser::getId, id));
            if (count > 0) throw new BusinessException("账号已被使用");
            user.setUsername(dto.getUsername());
        }
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        String icCardNo = StringUtils.hasText(dto.getIcCardNo()) ? dto.getIcCardNo().trim() : null;
        if (icCardNo != null && count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIcCardNo, icCardNo).ne(SysUser::getId, id)) > 0) {
            throw new BusinessException("IC卡号已被绑定: " + icCardNo);
        }
        user.setIcCardNo(icCardNo);
        user.setDeptId(dto.getDeptId());
        user.setHireDate(dto.getHireDate());
        user.setRemark(dto.getRemark());
        updateById(user);

        saveUserRoles(id, dto.getRoleIds());
        saveUserTeams(id, dto.getTeamIds());
        nameCacheEvictor.evictUser(id);
    }

    public void updateStatus(Long id, String status) {
        update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getStatus, status));
    }

    public void resetPassword(Long id) {
        update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getPassword, passwordEncoder.encode("123456")));
    }

    @Transactional
    public void deleteUser(Long id) {
        SysUser user = getById(id);
        if (user == null) throw new BusinessException("用户不存在");
        // 删除角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
        // 删除班组成员关联
        teamMemberMapper.delete(new LambdaQueryWrapper<BizTeamMember>()
                .eq(BizTeamMember::getUserId, id));
        removeById(id);
        nameCacheEvictor.evictUser(id);
    }

    public List<Map<String, Object>> getSelectList() {
        List<SysUser> users = list(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, "active")
                .orderByAsc(SysUser::getRealName));
        return users.stream().map(u -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("realName", u.getRealName());
            map.put("username", u.getUsername());
            return map;
        }).collect(Collectors.toList());
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    private void saveUserTeams(Long userId, List<Long> teamIds) {
        teamMemberMapper.physicalDeleteByUserId(userId);
        if (teamIds != null && !teamIds.isEmpty()) {
            for (Long teamId : teamIds) {
                BizTeamMember member = new BizTeamMember();
                member.setTeamId(teamId);
                member.setUserId(userId);
                member.setWeight(BigDecimal.ZERO);
                member.setJoinedAt(LocalDateTime.now());
                teamMemberMapper.insert(member);
            }
        }
    }
}

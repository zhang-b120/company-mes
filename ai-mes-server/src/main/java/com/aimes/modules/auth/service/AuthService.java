package com.aimes.modules.auth.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.JwtUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.auth.dto.ChangePasswordDTO;
import com.aimes.modules.auth.dto.LoginDTO;
import com.aimes.modules.auth.dto.RefreshTokenDTO;
import com.aimes.modules.auth.vo.LoginVO;
import com.aimes.modules.auth.vo.UserDetailVO;
import com.aimes.modules.system.entity.SysMenu;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysMenuMapper;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 认证服务
 * 处理用户登录、Token刷新、获取用户信息、修改密码等操作
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 登录失败最大尝试次数 */
    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    /** 账号锁定时长（分钟） */
    private static final int LOCK_MINUTES = 30;

    /**
     * 用户登录
     * 1. 校验用户是否存在、是否被禁用、是否被锁定
     * 2. 使用BCrypt校验密码，失败则累加login_fail_count，达到5次锁定30分钟
     * 3. 登录成功后重置失败计数，生成JWT的accessToken和refreshToken
     * 4. 返回用户基本信息、角色列表和权限列表
     *
     * @param dto 登录请求（包含username和password）
     * @return LoginVO 包含token和用户信息
     */
    public LoginVO login(LoginDTO dto) {
        // 根据用户名查询用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 校验账号状态：是否被禁用
        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用, 请联系管理员");
        }

        // 校验账号是否被锁定（登录失败次数过多）
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            long minutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), user.getLockUntil()) + 1;
            throw new BusinessException("账号已锁定, 请" + minutes + "分钟后重试");
        }

        // 使用BCrypt校验密码
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            // 密码错误：累加失败次数
            int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
            LambdaUpdateWrapper<SysUser> updateWrapper = new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, user.getId())
                    .set(SysUser::getLoginFailCount, failCount);

            // 失败次数达到上限，锁定账号30分钟
            if (failCount >= MAX_LOGIN_FAIL_COUNT) {
                updateWrapper.set(SysUser::getLockUntil, LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                userMapper.update(null, updateWrapper);
                throw new BusinessException("密码错误次数过多, 账号已锁定" + LOCK_MINUTES + "分钟");
            }

            userMapper.update(null, updateWrapper);
            int remaining = MAX_LOGIN_FAIL_COUNT - failCount;
            throw new BusinessException("用户名或密码错误, 还有" + remaining + "次机会");
        }

        // 登录成功：重置失败计数和锁定时间，更新最后登录时间
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLoginFailCount, 0)
                .set(SysUser::getLockUntil, null)
                .set(SysUser::getLastLoginAt, LocalDateTime.now()));

        // 查询用户角色列表
        List<String> roles = userMapper.selectRoleCodes(user.getId());

        // 查询权限列表：admin角色拥有所有按钮权限，其他角色按分配的权限查询
        List<String> permissions;
        if (roles.contains("admin")) {
            permissions = menuMapper.selectAllMenus().stream()
                    .filter(m -> "B".equals(m.getMenuType()) && m.getPermission() != null)
                    .map(SysMenu::getPermission)
                    .distinct().collect(Collectors.toList());
        } else {
            permissions = userMapper.selectPermissions(user.getId());
        }

        // 生成JWT Token
        LoginVO vo = new LoginVO();
        vo.setAccessToken(jwtUtil.generateAccessToken(user.getId()));
        vo.setRefreshToken(jwtUtil.generateRefreshToken(user.getId()));

        // 组装用户信息
        LoginVO.UserInfoVO userInfo = new LoginVO.UserInfoVO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setAvatar(user.getAvatarUrl());
        userInfo.setRoles(roles);
        userInfo.setPermissions(permissions);
        vo.setUserInfo(userInfo);

        return vo;
    }

    /**
     * 工位机刷卡登录（免密）
     * 通过 IC 卡号反查员工，刷卡器只输出卡号，不再携带任何用户信息。
     */
    public LoginVO cardLogin(String icCardNo) {
        if (icCardNo == null || icCardNo.isBlank()) {
            throw new BusinessException("IC卡号不能为空");
        }
        String trimmed = icCardNo.trim();
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getIcCardNo, trimmed));
        if (user == null) {
            throw new BusinessException("未识别的IC卡, 请联系管理员绑定员工: " + trimmed);
        }
        return issueWorkstationToken(user);
    }

    /**
     * 工位机手动账号免密登录（IC卡号过长不便手输时的备用入口）
     * 直接按员工账号查询，校验状态后签发 JWT。
     */
    public LoginVO cardLoginByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException("员工账号不能为空");
        }
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username.trim()));
        if (user == null) {
            throw new BusinessException("未找到该员工: " + username);
        }
        return issueWorkstationToken(user);
    }

    /** 工位机登录共用：状态校验 + 更新最后登录时间 + 签发 JWT */
    private LoginVO issueWorkstationToken(SysUser user) {
        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用, 请联系管理员");
        }
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            long minutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), user.getLockUntil()) + 1;
            throw new BusinessException("账号已锁定, 请" + minutes + "分钟后重试");
        }

        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLastLoginAt, LocalDateTime.now()));

        List<String> roles = userMapper.selectRoleCodes(user.getId());
        List<String> permissions;
        if (roles.contains("admin")) {
            permissions = menuMapper.selectAllMenus().stream()
                    .filter(m -> "B".equals(m.getMenuType()) && m.getPermission() != null)
                    .map(SysMenu::getPermission)
                    .distinct().collect(Collectors.toList());
        } else {
            permissions = userMapper.selectPermissions(user.getId());
        }

        LoginVO vo = new LoginVO();
        vo.setAccessToken(jwtUtil.generateAccessToken(user.getId()));
        vo.setRefreshToken(jwtUtil.generateRefreshToken(user.getId()));

        LoginVO.UserInfoVO userInfo = new LoginVO.UserInfoVO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setAvatar(user.getAvatarUrl());
        userInfo.setRoles(roles);
        userInfo.setPermissions(permissions);
        vo.setUserInfo(userInfo);

        return vo;
    }

    /**
     * 刷新Token
     * 校验refreshToken有效性，有效则重新签发accessToken和refreshToken
     *
     * @param dto 包含refreshToken的请求
     * @return 新的accessToken和refreshToken
     */
    public Map<String, String> refresh(RefreshTokenDTO dto) {
        Long userId = jwtUtil.parseToken(dto.getRefreshToken());
        if (userId == null) {
            throw new BusinessException(401, "RefreshToken无效或已过期");
        }
        Map<String, String> result = new HashMap<>();
        result.put("accessToken", jwtUtil.generateAccessToken(userId));
        result.put("refreshToken", jwtUtil.generateRefreshToken(userId));
        return result;
    }

    /**
     * 获取当前登录用户详细信息
     * 返回用户基本信息、角色列表、权限列表和菜单树（用于前端动态路由）
     * 菜单树中过滤掉按钮类型(B)节点，只保留目录(D)和菜单(M)
     *
     * @return UserDetailVO 包含roles, permissions, menus
     */
    public UserDetailVO getUserInfo() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || "disabled".equals(user.getStatus())) {
            throw new BusinessException(401, "用户不存在或已禁用");
        }

        // 查询角色列表
        List<String> roles = userMapper.selectRoleCodes(userId);
        List<String> permissions;
        List<SysMenu> menus;

        // admin角色拥有所有菜单和权限
        if (roles.contains("admin")) {
            menus = menuMapper.selectAllMenus();
            permissions = menus.stream()
                    .filter(m -> "B".equals(m.getMenuType()) && m.getPermission() != null)
                    .map(SysMenu::getPermission)
                    .distinct().collect(Collectors.toList());
        } else {
            menus = menuMapper.selectMenusByUserId(userId);
            permissions = userMapper.selectPermissions(userId);
        }

        // 组装返回结果
        UserDetailVO vo = new UserDetailVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setAvatar(user.getAvatarUrl());
        vo.setRoles(roles);
        vo.setPermissions(permissions);
        // 构建菜单树时过滤掉按钮类型，按钮不需要出现在菜单导航中
        vo.setMenus(buildMenuTree(menus));

        return vo;
    }

    /**
     * 修改密码
     * 校验旧密码后使用BCrypt加密新密码并保存
     *
     * @param dto 包含旧密码和新密码
     */
    public void changePassword(ChangePasswordDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        SysUser user = userMapper.selectById(userId);
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPassword, passwordEncoder.encode(dto.getNewPassword())));
    }

    /**
     * 构建菜单树
     * 将平铺的菜单列表转换为树形结构，过滤掉按钮类型(B)的节点
     * parentId为null或0的节点作为根节点
     *
     * @param menus 平铺的菜单列表
     * @return 树形菜单列表
     */
    private List<UserDetailVO.MenuVO> buildMenuTree(List<SysMenu> menus) {
        // 过滤掉按钮类型的菜单，按钮只用于权限控制，不出现在导航菜单中
        List<UserDetailVO.MenuVO> voList = menus.stream()
                .filter(m -> !"B".equals(m.getMenuType()))
                .map(m -> {
                    UserDetailVO.MenuVO vo = new UserDetailVO.MenuVO();
                    vo.setId(m.getId());
                    vo.setParentId(m.getParentId());
                    vo.setMenuName(m.getMenuName());
                    vo.setMenuType(m.getMenuType());
                    vo.setPlatform(m.getPlatform());
                    vo.setPath(m.getPath());
                    vo.setComponent(m.getComponent());
                    vo.setIcon(m.getIcon());
                    vo.setPermission(m.getPermission());
                    vo.setSortOrder(m.getSortOrder());
                    vo.setChildren(new ArrayList<>());
                    return vo;
                }).collect(Collectors.toList());

        // 构建id -> MenuVO的映射，用于快速查找父节点
        Map<Long, UserDetailVO.MenuVO> map = voList.stream()
                .collect(Collectors.toMap(UserDetailVO.MenuVO::getId, v -> v));

        // 组装树形结构
        List<UserDetailVO.MenuVO> tree = new ArrayList<>();
        for (UserDetailVO.MenuVO vo : voList) {
            if (vo.getParentId() == null || vo.getParentId() == 0) {
                tree.add(vo);
            } else {
                UserDetailVO.MenuVO parent = map.get(vo.getParentId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                }
            }
        }

        // 按sortOrder排序
        tree.sort(Comparator.comparingInt(v -> v.getSortOrder() != null ? v.getSortOrder() : 0));

        return tree;
    }
}

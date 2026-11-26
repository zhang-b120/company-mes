package com.aimes.common.interceptor;

import com.aimes.common.utils.JwtUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.system.entity.SysRole;
import com.aimes.modules.system.entity.SysRoleDataScope;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.entity.SysUserRole;
import com.aimes.modules.system.mapper.SysDeptMapper;
import com.aimes.modules.system.mapper.SysRoleDataScopeMapper;
import com.aimes.modules.system.mapper.SysRoleMapper;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JWT认证拦截器
 * 拦截所有非白名单请求，校验Authorization头中的Bearer Token
 * 校验通过后将userId存入ThreadLocal（SecurityUtil），请求结束后自动清除
 * 同时加载用户的数据权限范围到SecurityUtil.DataScopeContext
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleDataScopeMapper roleDataScopeMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            sendError(response, 401, "未登录或Token已过期");
            return false;
        }

        String token = authHeader.substring(7);
        Long userId = jwtUtil.parseToken(token);
        if (userId == null) {
            sendError(response, 401, "Token无效或已过期");
            return false;
        }

        SecurityUtil.setCurrentUserId(userId);
        loadDataScopeContext(userId);

        return true;
    }

    /**
     * 加载用户数据权限上下文
     * 策略: 取用户所有角色中权限最大的那个 (all > dept > custom > self)
     *
     * dept 模式：基于部门层级。取用户的 dept_id，通过递归 CTE 查出该部门
     * 及所有子孙部门的 ID，SQL 过滤条件为：
     *   create_by IN (SELECT id FROM sys_user WHERE dept_id IN (...))
     */
    private void loadDataScopeContext(Long userId) {
        try {
            List<SysUserRole> userRoles = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
            if (userRoles.isEmpty()) {
                SecurityUtil.setDataScopeContext(new SecurityUtil.DataScopeContext("self", null, null));
                return;
            }

            List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            List<SysRole> roles = roleMapper.selectBatchIds(roleIds);

            // 优先级: all > dept > custom > self
            String bestScope = "self";
            for (SysRole role : roles) {
                String ds = role.getDataScope();
                if (ds == null) continue;
                if ("all".equals(ds)) { bestScope = "all"; break; }
                if ("dept".equals(ds) && !"all".equals(bestScope)) bestScope = "dept";
                if ("custom".equals(ds) && "self".equals(bestScope)) bestScope = "custom";
            }

            SecurityUtil.DataScopeContext ctx = new SecurityUtil.DataScopeContext();
            ctx.setScope(bestScope);

            if ("dept".equals(bestScope)) {
                // 递归查询用户所在部门及全部子孙部门ID
                SysUser user = userMapper.selectById(userId);
                if (user != null && user.getDeptId() != null) {
                    List<Long> deptIds = deptMapper.selectAllDescendantIds(user.getDeptId());
                    ctx.setDeptIds(deptIds.isEmpty() ? null : deptIds);
                }
                // 用户未分配部门时退化为 self
                if (ctx.getDeptIds() == null || ctx.getDeptIds().isEmpty()) {
                    ctx.setScope("self");
                }
            } else if ("custom".equals(bestScope)) {
                // 收集所有角色的自定义数据权限配置中的用户ID
                List<SysRoleDataScope> scopes = roleDataScopeMapper.selectList(
                        new LambdaQueryWrapper<SysRoleDataScope>()
                                .in(SysRoleDataScope::getRoleId, roleIds)
                                .eq(SysRoleDataScope::getScopeType, "user"));
                List<Long> customUserIds = scopes.stream()
                        .map(s -> Long.valueOf(s.getScopeValue()))
                        .distinct()
                        .collect(Collectors.toList());
                // 始终包含自己
                if (!customUserIds.contains(userId)) customUserIds.add(userId);
                ctx.setCustomUserIds(customUserIds);
            }

            SecurityUtil.setDataScopeContext(ctx);
        } catch (Exception e) {
            // 加载失败时默认 self 权限
            SecurityUtil.setDataScopeContext(new SecurityUtil.DataScopeContext("self", null, null));
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SecurityUtil.clear();
    }

    private void sendError(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

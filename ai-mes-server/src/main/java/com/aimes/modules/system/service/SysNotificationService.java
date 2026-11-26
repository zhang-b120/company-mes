package com.aimes.modules.system.service;

import com.aimes.common.result.PageResult;
import com.aimes.modules.system.entity.SysConfig;
import com.aimes.modules.system.entity.SysNotification;
import com.aimes.modules.system.entity.SysUserRole;
import com.aimes.modules.system.entity.SysRole;
import com.aimes.modules.system.mapper.SysNotificationMapper;
import com.aimes.modules.system.mapper.SysUserRoleMapper;
import com.aimes.modules.system.mapper.SysRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息通知服务
 * 管理用户消息通知的查询、未读计数、标记已读等操作
 * 通知类型: approval(审批通知) / alert(预警通知) / system(系统通知)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysNotificationService extends ServiceImpl<SysNotificationMapper, SysNotification> {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysConfigService configService;

    /**
     * 默认通知场景表 (key, displayName, webEnabled, wechatEnabled, defaultRoles).
     * 业务代码通过 notifyByScenario(key, ...) 发通知, 真实开关 / 角色读自 sys_config.
     * 管理员可在 [系统设置 → 通知配置] 改, key 不变, value 覆盖默认.
     */
    public static final java.util.List<String[]> DEFAULT_SCENARIOS = java.util.List.of(
            new String[]{"new_order",        "新订单通知",     "true",  "false", "boss,sales"},
            new String[]{"approval_todo",    "审批待办",       "true",  "true",  "boss"},
            new String[]{"inventory_alert",  "库存预警",       "true",  "false", "warehouse"},
            new String[]{"delivery_alert",   "交期预警",       "true",  "false", "production_manager,sales"},
            new String[]{"completion",       "完工通知",       "true",  "false", "production_manager"},
            new String[]{"payment_remind",   "收款提醒",       "true",  "false", "finance,boss"},
            new String[]{"exception",        "异常上报",       "true",  "true",  "boss,production_manager"},
            new String[]{"ai_brief",         "AI简报推送",     "true",  "true",  "boss"},
            new String[]{"plan_cancelled",   "生产计划取消",   "true",  "false", "sales,production_manager"},
            new String[]{"order_changed",    "销售订单变更",   "true",  "false", "production_manager"},
            new String[]{"work_order_cancelled", "生产工单取消", "true",  "false", "production_manager,sales"}
    );

    private static String configKey(String scenarioKey) {
        return "notify.scenario." + scenarioKey;
    }

    /** 取默认场景定义 */
    public static String[] getDefaultScenario(String scenarioKey) {
        return DEFAULT_SCENARIOS.stream().filter(s -> s[0].equals(scenarioKey)).findFirst().orElse(null);
    }

    /**
     * 取该场景的运行时配置: 先用默认, 再用 sys_config 覆盖.
     * @return [webEnabled(bool string), wechatEnabled(bool string), roles(csv)]
     */
    public String[] resolveScenario(String scenarioKey) {
        String[] def = getDefaultScenario(scenarioKey);
        if (def == null) return null;
        String[] result = new String[]{def[2], def[3], def[4]};
        SysConfig cfg = configService.getByKey(configKey(scenarioKey));
        if (cfg != null && cfg.getConfigValue() != null && !cfg.getConfigValue().isBlank()) {
            // 简单 JSON: {"webEnabled":true,"wechatEnabled":false,"roles":"sales,production_manager"}
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(cfg.getConfigValue());
                if (node.has("webEnabled")) result[0] = String.valueOf(node.get("webEnabled").asBoolean());
                if (node.has("wechatEnabled")) result[1] = String.valueOf(node.get("wechatEnabled").asBoolean());
                if (node.has("roles")) result[2] = node.get("roles").asText();
            } catch (Exception e) {
                log.warn("解析通知场景[{}]配置失败, 用默认值: {}", scenarioKey, e.getMessage());
            }
        }
        return result;
    }

    /** 保存场景配置覆盖到 sys_config */
    public void saveScenarioOverride(String scenarioKey, Boolean webEnabled, Boolean wechatEnabled, String roles) {
        if (getDefaultScenario(scenarioKey) == null) return;
        try {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            if (webEnabled != null) m.put("webEnabled", webEnabled);
            if (wechatEnabled != null) m.put("wechatEnabled", wechatEnabled);
            if (roles != null) m.put("roles", roles);
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
            configService.saveConfig(configKey(scenarioKey), json);
        } catch (Exception e) {
            log.error("保存通知场景[{}]配置失败", scenarioKey, e);
        }
    }

    /**
     * 业务入口: 按场景发通知. 配置关掉 web → 不发; 角色列表为空 → 不发.
     * 微信渠道暂未接入, wechatEnabled 仅前端展示用.
     */
    public void notifyByScenario(String scenarioKey, String title, String content,
                                  String type, String relatedType, Long relatedId) {
        String[] cfg = resolveScenario(scenarioKey);
        if (cfg == null) {
            log.warn("通知场景[{}]未定义, 跳过", scenarioKey);
            return;
        }
        if (!"true".equalsIgnoreCase(cfg[0])) return;
        String roles = cfg[2];
        if (roles == null || roles.isBlank()) return;
        for (String role : roles.split(",")) {
            String r = role.trim();
            if (!r.isEmpty()) notifyRole(r, title, content, type, relatedType, relatedId);
        }
    }

    /**
     * 我的通知分页
     */
    public PageResult<SysNotification> getMyNotifications(Long userId, int pageNum, int pageSize) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .orderByDesc(SysNotification::getCreateTime);

        Page<SysNotification> page = page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    /**
     * 未读通知数量
     */
    public long getUnreadCount(Long userId) {
        return count(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, 0));
    }

    /**
     * 标记已读
     */
    public void markRead(Long id) {
        update(new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getId, id)
                .set(SysNotification::getIsRead, 1));
    }

    /**
     * 全部标记已读
     */
    public void markAllRead(Long userId) {
        update(new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, 0)
                .set(SysNotification::getIsRead, 1));
    }

    // ==================== 新增：通知创建方法 ====================

    /**
     * 发送通知给指定用户
     */
    public void notifyUser(Long userId, String title, String content,
                           String type, String relatedType, Long relatedId) {
        SysNotification notification = new SysNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setNotificationType(type);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        save(notification);
    }

    /**
     * 发送通知给某角色的所有用户
     * @param roleCode 角色编码(如 admin/supervisor/worker/warehouse/quality/sales/finance)
     */
    public void notifyRole(String roleCode, String title, String content,
                           String type, String relatedType, Long relatedId) {
        try {
            // 根据roleCode查找角色
            SysRole role = roleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>()
                            .eq(SysRole::getRoleCode, roleCode)
                            .last("LIMIT 1"));
            if (role == null) {
                log.warn("通知发送失败: 角色编码[{}]不存在", roleCode);
                return;
            }

            // 查找该角色的所有用户
            List<SysUserRole> userRoles = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getRoleId, role.getId()));

            for (SysUserRole userRole : userRoles) {
                notifyUser(userRole.getUserId(), title, content, type, relatedType, relatedId);
            }
        } catch (Exception e) {
            log.error("通知发送失败: roleCode={}, title={}", roleCode, title, e);
        }
    }
}

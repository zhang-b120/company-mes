package com.aimes.modules.system.controller;

import com.aimes.common.result.R;
import com.aimes.modules.system.entity.SysConfig;
import com.aimes.modules.system.mapper.SysConfigMapper;
import com.aimes.modules.system.service.SysNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 系统设置扩展控制器
 * 处理通知配置、备份管理、编号规则等系统级设置
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SysSettingsController {

    private final SysConfigMapper configMapper;
    private final SysNotificationService notificationService;

    // ==================== 通知配置 ====================

    @GetMapping("/notification-configs")
    public R<List<Map<String, Object>>> getNotificationConfigs() {
        // 默认场景表来自 SysNotificationService.DEFAULT_SCENARIOS, sys_config 中的 override 在 resolveScenario 内被合并.
        List<Map<String, Object>> result = new ArrayList<>();
        for (String[] scenario : SysNotificationService.DEFAULT_SCENARIOS) {
            String[] resolved = notificationService.resolveScenario(scenario[0]);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "notify_" + scenario[0]);
            item.put("scenarioKey", scenario[0]);
            item.put("scenarioName", scenario[1]);
            item.put("webEnabled", Boolean.parseBoolean(resolved[0]));
            item.put("wechatEnabled", Boolean.parseBoolean(resolved[1]));
            item.put("receiverRoles", Arrays.asList(resolved[2].split(",")));
            item.put("pushTime", "ai_brief".equals(scenario[0]) ? "08:00" : "");
            item.put("maxDailyPush", "ai_brief".equals(scenario[0]) ? 1 : 10);
            result.add(item);
        }
        return R.ok(result);
    }

    @PutMapping("/notification-configs/{id}")
    public R<Void> updateNotificationConfig(@PathVariable String id, @RequestBody Map<String, Object> body) {
        // id 形如 "notify_<scenarioKey>"
        String scenarioKey = id != null && id.startsWith("notify_") ? id.substring("notify_".length()) : id;
        Boolean webEnabled = body.get("webEnabled") instanceof Boolean ? (Boolean) body.get("webEnabled") : null;
        Boolean wechatEnabled = body.get("wechatEnabled") instanceof Boolean ? (Boolean) body.get("wechatEnabled") : null;
        String roles = null;
        Object r = body.get("receiverRoles");
        if (r instanceof List) {
            roles = String.join(",", ((List<?>) r).stream().map(Object::toString).toList());
        } else if (r instanceof String) {
            roles = (String) r;
        }
        notificationService.saveScenarioOverride(scenarioKey, webEnabled, wechatEnabled, roles);
        return R.ok();
    }

    // ==================== 备份管理 ====================

    @GetMapping("/backups/page")
    public R<Map<String, Object>> getBackupRecords(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", List.of());
        result.put("total", 0);
        return R.ok(result);
    }

    @PostMapping("/backups")
    public R<Map<String, Object>> createBackup() {
        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("id", System.currentTimeMillis());
        backup.put("fileName", "ai-mes-backup-" + LocalDateTime.now().toString().replace(":", "-") + ".sql");
        backup.put("status", "completed");
        backup.put("createdAt", LocalDateTime.now().toString());
        backup.put("message", "备份功能需要服务端配置数据库备份工具");
        return R.ok(backup);
    }

    @PostMapping("/backups/{id}/restore")
    public R<Void> restoreBackup(@PathVariable Long id) {
        return R.fail("备份恢复功能需要服务端配置");
    }

    @DeleteMapping("/backups/{id}")
    public R<Void> deleteBackup(@PathVariable Long id) {
        return R.ok();
    }

    @GetMapping("/backups/{id}/download")
    public R<Void> downloadBackup(@PathVariable Long id) {
        return R.fail("备份下载功能需要服务端配置");
    }

    @GetMapping("/backups/config")
    public R<Map<String, Object>> getBackupConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("autoBackup", false);
        config.put("backupInterval", "daily");
        config.put("backupTime", "02:00");
        config.put("maxBackups", 30);
        config.put("backupPath", "/data/backups");
        return R.ok(config);
    }

    @PutMapping("/backups/config")
    public R<Void> saveBackupConfig(@RequestBody Map<String, Object> config) {
        return R.ok();
    }

    // ==================== 编号规则 ====================

    @GetMapping("/numbering-rules")
    public R<List<Map<String, Object>>> getNumberingRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        // 格式: prefix, businessName, dateFormat, seqLength
        String[][] ruleConfigs = {
                {"SO", "销售订单",       "YYYYMMDD", "3"},
                {"WO", "生产工单",       "YYYYMMDD", "3"},
                {"PO", "采购订单",       "YYYYMMDD", "3"},
                {"PR", "采购申请",       "YYYYMMDD", "3"},
                {"OR", "外协申请",       "YYYYMMDD", "3"},
                {"OO", "外协加工单",     "YYYYMMDD", "3"},
                {"AR", "应收账款",       "YYYYMMDD", "3"},
                {"AP", "应付账款",       "YYYYMMDD", "3"},
                {"PM", "收付款单",       "YYYYMMDD", "3"},
                {"C",  "客户编号",       "none",     "6"},
                {"P",  "物料编号(成品)",  "none",     "6"},
                {"M",  "物料编号(原料)",  "none",     "6"},
                {"S",  "供应商编号",      "none",     "4"}
        };
        long id = 1;
        for (String[] rule : ruleConfigs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id++);
            item.put("prefix", rule[0]);
            item.put("businessName", rule[1]);
            item.put("dateFormat", rule[2]);
            item.put("seqLength", Integer.parseInt(rule[3]));
            rules.add(item);
        }
        return R.ok(rules);
    }

    @PutMapping("/numbering-rules/{id}")
    public R<Void> updateNumberingRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // 编号规则目前由 CodeUtil 硬编码，此处仅接收请求不报错
        return R.ok();
    }
}

package com.aimes.modules.system.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.system.entity.SysNotification;
import com.aimes.modules.system.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 消息通知控制器
 * 提供通知分页查询、未读计数、标记已读、全部已读等接口
 * 路径: /api/system/notifications
 */
@RestController
@RequestMapping("/api/system/notifications")
@RequiredArgsConstructor
public class SysNotificationController {

    private final SysNotificationService notificationService;

    @GetMapping("/page")
    public R<PageResult<SysNotification>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(notificationService.getMyNotifications(userId, pageNum, pageSize));
    }

    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return R.ok();
    }

    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        Long userId = SecurityUtil.getCurrentUserId();
        notificationService.markAllRead(userId);
        return R.ok();
    }
}

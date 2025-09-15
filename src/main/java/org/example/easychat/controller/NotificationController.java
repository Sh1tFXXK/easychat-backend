package org.example.easychat.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.easychat.service.NotificationService;
import org.example.easychat.BO.ApiResponseBO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 通知设置控制器
 */
@Slf4j
@RestController
@RequestMapping("/user/notification")
@Validated
public class NotificationController {
    
    @Autowired
    private NotificationService notificationService;
    
    /**
     * 更新用户通知设置
     */
    @PutMapping("/settings")
    public ApiResponseBO<Void> updateNotificationSettings(
            @RequestParam @NotBlank String userId,
            @RequestBody @NotNull Map<String, Boolean> settings) {
        
        try {
            notificationService.updateUserNotificationSettings(userId, settings);
            return ApiResponseBO.success(null, "通知设置更新成功");
        } catch (Exception e) {
            log.error("更新通知设置失败: userId={}", userId, e);
            return ApiResponseBO.error("更新通知设置失败");
        }
    }
    
    /**
     * 设置自定义通知模板
     */
    @PutMapping("/template")
    public ApiResponseBO<Void> setNotificationTemplate(
            @RequestParam @NotBlank String type,
            @RequestParam @NotBlank String template) {
        
        try {
            notificationService.setNotificationTemplate(type, template);
            return ApiResponseBO.success(null, "通知模板设置成功");
        } catch (Exception e) {
            log.error("设置通知模板失败: type={}", type, e);
            return ApiResponseBO.error("设置通知模板失败");
        }
    }
    
    /**
     * 手动重试失败的通知
     */
    @PostMapping("/retry")
    public ApiResponseBO<Void> retryFailedNotifications() {
        try {
            notificationService.retryFailedNotifications();
            return ApiResponseBO.success(null, "重试任务已启动");
        } catch (Exception e) {
            log.error("手动重试失败", e);
            return ApiResponseBO.error("重试任务启动失败");
        }
    }
}
package org.example.easychat.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.example.easychat.BO.ApiResponseBO;
import org.example.easychat.VO.AttentionCheckResult;
import org.example.easychat.VO.AttentionUpdatesResult;
import org.example.easychat.VO.BatchOperationResult;
import org.example.easychat.VO.SpecialAttentionVO;
import org.example.easychat.dto.*;
import org.example.easychat.service.RateLimitService;
import org.example.easychat.service.SpecialAttentionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

/**
 * 特别关心控制器
 * 提供特别关心功能的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/user/special-attention")
@Api(tags = "特别关心管理", description = "特别关心功能相关接口")
public class SpecialAttentionController {

    @Autowired
    private SpecialAttentionService attentionService;

    @Autowired
    private RateLimitService rateLimitService;

    /**
     * 获取特别关心列表
     * 
     * @param queryDTO 查询参数
     * @param request HTTP请求对象
     * @return 特别关心列表
     */
    @ApiOperation(value = "获取特别关心列表", notes = "获取用户的特别关心列表，支持分页和状态筛选")
    @GetMapping
    public ApiResponseBO<org.example.easychat.Entity.PageResult<SpecialAttentionVO>> getAttentionList(
            @Valid @ModelAttribute AttentionQueryDTO queryDTO,
            HttpServletRequest request) {
        
        // IP限流检查
        String clientIp = getClientIpAddress(request);
        rateLimitService.checkIpRateLimit(clientIp, "query_attention");
        
        org.example.easychat.Entity.PageResult<SpecialAttentionVO> result = attentionService.getAttentionList(queryDTO);
        return ApiResponseBO.success(result);
    }

    /**
     * 添加特别关心
     * 
     * @param addDTO 添加特别关心的请求参数
     * @param request HTTP请求对象
     * @return 添加成功的特别关心信息
     */
    @ApiOperation(value = "添加特别关心", notes = "为用户添加一个特别关心关系")
    @PostMapping
    public ApiResponseBO<SpecialAttentionVO> addAttention(
            @Valid @RequestBody AttentionAddDTO addDTO,
            HttpServletRequest request) {
        
        log.info("收到添加特别关心请求: userId={}, targetUserId={}", addDTO.getUserId(), addDTO.getTargetUserId());
        
        // IP限流检查
        String clientIp = getClientIpAddress(request);
        rateLimitService.checkIpRateLimit(clientIp, "add_attention");
        
        SpecialAttentionVO result = attentionService.addAttention(addDTO);
        log.info("添加特别关心成功: userId={}, targetUserId={}", addDTO.getUserId(), addDTO.getTargetUserId());
        return ApiResponseBO.success(result, "添加特别关心成功");
    }
    
    /**
     * 删除特别关心
     * 
     * @param removeDTO 删除特别关心的请求参数
     * @param request HTTP请求对象
     * @return 操作结果
     */
    @ApiOperation(value = "删除特别关心", notes = "删除用户的特别关心关系")
    @DeleteMapping
    public ApiResponseBO<Void> removeAttention(
            @Valid @RequestBody AttentionRemoveDTO removeDTO,
            HttpServletRequest request) {
        
        // IP限流检查
        String clientIp = getClientIpAddress(request);
        rateLimitService.checkIpRateLimit(clientIp, "remove_attention");
        
        attentionService.removeAttention(removeDTO);
        return ApiResponseBO.success("取消特别关心成功");
    }
    
    /**
     * 根据目标用户ID删除特别关心
     * 
     * @param removeDTO 删除特别关心的请求参数
     * @param request HTTP请求对象
     * @return 操作结果
     */
    @ApiOperation(value = "根据目标用户ID删除特别关心", notes = "根据目标用户ID删除特别关心关系")
    @DeleteMapping("/by-user")
    public ApiResponseBO<Void> removeAttentionByUser(
            @Valid @RequestBody AttentionRemoveByUserDTO removeDTO,
            HttpServletRequest request) {
        
        // IP限流检查
        String clientIp = getClientIpAddress(request);
        rateLimitService.checkIpRateLimit(clientIp, "remove_attention_by_user");
        
        // 创建AttentionRemoveDTO对象
        AttentionRemoveDTO attentionRemoveDTO = new AttentionRemoveDTO();
        attentionRemoveDTO.setUserId(removeDTO.getUserId());
        attentionRemoveDTO.setTargetUserId(removeDTO.getTargetUserId());
        
        attentionService.removeAttention(attentionRemoveDTO);
        return ApiResponseBO.success("取消特别关心成功");
    }
    
    /**
     * 更新特别关心设置
     * 
     * @param updateDTO 更新特别关心设置的请求参数
     * @param request HTTP请求对象
     * @return 操作结果
     */
    @ApiOperation(value = "更新特别关心设置", notes = "更新特别关心的通知设置")
    @PutMapping
    public ApiResponseBO<Void> updateAttention(
            @Valid @RequestBody AttentionUpdateDTO updateDTO,
            HttpServletRequest request) {
        
        // IP限流检查
        String clientIp = getClientIpAddress(request);
        rateLimitService.checkIpRateLimit(clientIp, "update_attention");
        
        attentionService.updateNotificationSettings(updateDTO);
        return ApiResponseBO.success("更新设置成功");
    }
    
    /**
     * 批量操作特别关心
     * 
     * @param batchDTO 批量操作的请求参数
     * @param request HTTP请求对象
     * @return 批量操作结果
     */
    @ApiOperation(value = "批量操作特别关心", notes = "批量添加或删除特别关心关系")
    @PostMapping("/batch")
    public ApiResponseBO<BatchOperationResult> batchOperation(
            @Valid @RequestBody AttentionBatchDTO batchDTO,
            HttpServletRequest request) {
        
        // IP限流检查
        String clientIp = getClientIpAddress(request);
        rateLimitService.checkIpRateLimit(clientIp, "batch_operation");
        
        BatchOperationResult result = attentionService.batchOperation(batchDTO);
        return ApiResponseBO.success(result, "批量操作完成");
    }
    
    /**
     * 检查特别关心状态
     * 
     * @param userId 用户ID
     * @param targetUserId 目标用户ID
     * @param request HTTP请求对象
     * @return 特别关心状态检查结果
     */
    @ApiOperation(value = "检查特别关心状态", notes = "检查两个用户之间是否存在特别关心关系")
    @GetMapping("/check")
    public ApiResponseBO<AttentionCheckResult> checkAttentionStatus(
            @ApiParam(value = "用户ID", required = true) @RequestParam @NotBlank String userId,
            @ApiParam(value = "目标用户ID", required = true) @RequestParam @NotBlank String targetUserId,
            HttpServletRequest request) {
        
        // IP限流检查
        String clientIp = getClientIpAddress(request);
        rateLimitService.checkIpRateLimit(clientIp, "check_attention");
        
        AttentionCheckResult result = attentionService.checkAttentionStatus(userId, targetUserId);
        return ApiResponseBO.success(result);
    }
    
    /**
     * 获取特别关心更新
     * 
     * @param updatesDTO 获取更新的请求参数
     * @param request HTTP请求对象
     * @return 特别关心更新结果
     */
    @ApiOperation(value = "获取特别关心更新", notes = "获取指定时间之后的特别关心更新")
    @GetMapping("/updates")
    public ApiResponseBO<AttentionUpdatesResult> getAttentionUpdates(
            @Valid @ModelAttribute AttentionUpdatesDTO updatesDTO,
            HttpServletRequest request) {
        
        // IP限流检查
        String clientIp = getClientIpAddress(request);
        rateLimitService.checkIpRateLimit(clientIp, "get_updates");
        
        AttentionUpdatesResult result = attentionService.getAttentionUpdates(updatesDTO);
        return ApiResponseBO.success(result);
    }
    
    /**
     * 获取客户端IP地址
     * 
     * @param request HTTP请求对象
     * @return 客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
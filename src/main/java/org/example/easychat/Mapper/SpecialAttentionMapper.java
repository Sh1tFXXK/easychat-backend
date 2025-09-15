package org.example.easychat.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.easychat.Entity.SpecialAttention;
import org.example.easychat.VO.AttentionStatistics;
import org.example.easychat.VO.SpecialAttentionVO;

import java.util.List;
import java.util.Map;

@Mapper
public interface SpecialAttentionMapper extends BaseMapper<SpecialAttention> {
    
    /**
     * 获取用户的特别关心列表（包含用户信息和状态）
     */
    List<SpecialAttentionVO> getAttentionListWithUserInfo(
        @Param("userId") String userId,
        @Param("status") String status,
        @Param("offset") Integer offset,
        @Param("limit") Integer limit
    );
    
    /**
     * 获取关心指定用户的所有用户ID列表
     */
    List<String> getUsersAttentionTo(@Param("targetUserId") String targetUserId);
    
    /**
     * 批量插入特别关心记录
     */
    int batchInsert(@Param("attentions") List<SpecialAttention> attentions);
    
    /**
     * 批量删除特别关心记录
     */
    int batchDelete(@Param("userId") String userId, @Param("targetUserIds") List<String> targetUserIds);
    
    /**
     * 检查特别关心关系是否存在
     */
    boolean existsAttention(@Param("userId") String userId, @Param("targetUserId") String targetUserId);
    
    /**
     * 获取用户特别关心统计信息
     */
    AttentionStatistics getAttentionStatistics(@Param("userId") String userId);
    
    /**
     * 获取互相关注的用户列表
     */
    List<SpecialAttentionVO> getMutualAttentions(
        @Param("userId") String userId,
        @Param("offset") Integer offset,
        @Param("limit") Integer limit
    );
    
    /**
     * 根据用户状态统计关注数量
     */
    List<Map<String, Object>> getAttentionCountByStatus(@Param("userId") String userId);
    
    /**
     * 获取最近活跃的关注用户
     */
    List<SpecialAttentionVO> getRecentActiveAttentions(
        @Param("userId") String userId,
        @Param("hours") Integer hours,
        @Param("limit") Integer limit
    );
    
    /**
     * 搜索关注用户
     */
    List<SpecialAttentionVO> searchAttentions(
        @Param("userId") String userId,
        @Param("keyword") String keyword,
        @Param("offset") Integer offset,
        @Param("limit") Integer limit
    );
    
    /**
     * 获取关注用户的消息统计
     */
    @MapKey("status")
    List<Map<String, Object>> getAttentionMessageStats( @Param("userId") String userId);
}
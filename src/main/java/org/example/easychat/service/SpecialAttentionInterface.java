package org.example.easychat.service;

import org.example.easychat.Entity.PageResult;
import org.example.easychat.VO.AttentionCheckResult;
import org.example.easychat.VO.AttentionUpdatesResult;
import org.example.easychat.VO.BatchOperationResult;
import org.example.easychat.VO.SpecialAttentionVO;
import org.example.easychat.dto.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

public interface SpecialAttentionInterface {
    PageResult<SpecialAttentionVO> getAttentionList(AttentionQueryDTO queryDTO);

    SpecialAttentionVO addAttention(@Valid AttentionAddDTO addDTO);

    void removeAttention(@Valid AttentionRemoveDTO removeDTO);

    void updateNotificationSettings(AttentionUpdateDTO updateDTO);

    BatchOperationResult batchOperation(@Valid AttentionBatchDTO batchDTO);

    AttentionCheckResult checkAttentionStatus(@NotBlank String userId, @NotBlank String targetUserId);

    AttentionUpdatesResult getAttentionUpdates(@Valid AttentionUpdatesDTO updatesDTO);

    org.example.easychat.VO.AttentionStatistics getAttentionStatistics(@NotBlank String userId);
}

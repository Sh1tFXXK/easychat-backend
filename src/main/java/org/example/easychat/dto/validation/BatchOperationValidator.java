package org.example.easychat.dto.validation;

import org.example.easychat.dto.AttentionBatchDTO;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class BatchOperationValidator implements ConstraintValidator<ValidBatchOperation, AttentionBatchDTO> {
    
    @Override
    public void initialize(ValidBatchOperation constraintAnnotation) {
        // 初始化方法，可以为空
    }
    
    @Override
    public boolean isValid(AttentionBatchDTO dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return false;
        }
        
        // 如果操作是添加，则必须提供通知设置
        if (dto.getOperationType() == AttentionBatchDTO.BatchOperationType.ADD) {
            return dto.getItems() != null && !dto.getItems().isEmpty() &&
                   dto.getItems().stream().allMatch(item -> item.getNotificationSettings() != null);
        }
        
        // 其他操作不需要通知设置
        return true;
    }
}
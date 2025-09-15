package org.example.easychat.dto.validation;

import org.example.easychat.dto.AttentionRemoveDTO;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class RemoveRequestValidator implements ConstraintValidator<ValidRemoveRequest, AttentionRemoveDTO> {
    
    @Override
    public void initialize(ValidRemoveRequest constraintAnnotation) {
        // 初始化方法，可以为空
    }
    
    @Override
    public boolean isValid(AttentionRemoveDTO dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return false;
        }
        
        // 必须提供ID或目标用户ID中的一个
        boolean hasId = dto.getId() != null;
        boolean hasTargetUserId = dto.getTargetUserId() != null && !dto.getTargetUserId().trim().isEmpty();
        
        return hasId || hasTargetUserId;
    }
}
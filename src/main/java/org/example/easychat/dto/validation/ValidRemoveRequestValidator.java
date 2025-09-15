package org.example.easychat.dto.validation;

import org.example.easychat.dto.AttentionRemoveDTO;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class ValidRemoveRequestValidator implements ConstraintValidator<ValidRemoveRequest, AttentionRemoveDTO> {

    @Override
    public void initialize(ValidRemoveRequest constraintAnnotation) {
        // 初始化方法，可以为空
    }

    @Override
    public boolean isValid(AttentionRemoveDTO dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return false;
        }
        
        // 检查id和targetUserId至少有一个不为空
        boolean hasId = dto.getId() != null;
        boolean hasTargetUserId = dto.getTargetUserId() != null && !dto.getTargetUserId().trim().isEmpty();
        
        return hasId || hasTargetUserId;
    }
}
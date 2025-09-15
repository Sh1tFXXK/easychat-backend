package org.example.easychat.dto.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidRemoveRequestValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRemoveRequest {
    String message() default "删除请求参数无效：id和targetUserId至少需要提供一个";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
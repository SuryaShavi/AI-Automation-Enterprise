package com.aieap.platform.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = SafeStringMapValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeStringMap {

    String message() default "contains unsupported key or value";

    int maxEntries() default 20;

    int maxKeyLength() default 64;

    int maxValueLength() default 256;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
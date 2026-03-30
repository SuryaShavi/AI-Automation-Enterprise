package com.aieap.platform.workflow.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidWorkflowTriggerValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidWorkflowTrigger {

    String message() default "contains invalid workflow trigger configuration";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
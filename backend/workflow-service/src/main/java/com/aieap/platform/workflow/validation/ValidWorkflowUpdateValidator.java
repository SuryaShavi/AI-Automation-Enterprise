package com.aieap.platform.workflow.validation;

import com.aieap.platform.workflow.WorkflowApi.WorkflowUpdateRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidWorkflowUpdateValidator implements ConstraintValidator<ValidWorkflowUpdate, WorkflowUpdateRequest> {

    @Override
    public boolean isValid(WorkflowUpdateRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return hasText(value.status()) || hasText(value.name()) || value.triggers() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
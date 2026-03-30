package com.aieap.platform.workflow.validation;

import com.aieap.platform.workflow.WorkflowApi.WorkflowTriggerRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.Set;

public class ValidWorkflowTriggerValidator implements ConstraintValidator<ValidWorkflowTrigger, WorkflowTriggerRequest> {

    private static final Set<String> ALLOWED_TYPES = Set.of("MANUAL", "SCHEDULE", "EVENT", "WEBHOOK");

    @Override
    public boolean isValid(WorkflowTriggerRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String type = normalize(value.type());
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            return false;
        }
        if ("SCHEDULE".equals(type)) {
            return normalize(value.scheduleCron()) != null;
        }
        if ("EVENT".equals(type)) {
            return normalize(value.eventType()) != null;
        }
        if ("WEBHOOK".equals(type)) {
            return normalize(value.provider()) != null;
        }
        return true;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
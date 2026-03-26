package com.aieap.platform.workflow;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkflowTriggerScheduler {

    private final WorkflowRuntimeService workflowRuntimeService;

    public WorkflowTriggerScheduler(WorkflowRuntimeService workflowRuntimeService) {
        this.workflowRuntimeService = workflowRuntimeService;
    }

    @Scheduled(fixedDelayString = "${workflow.triggers.poll-interval-ms:15000}")
    public void processDueTriggers() {
        workflowRuntimeService.processDueScheduledTriggers();
    }
}
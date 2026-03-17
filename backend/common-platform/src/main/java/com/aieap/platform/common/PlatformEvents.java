package com.aieap.platform.common;

public final class PlatformEvents {
    public static final String EMAIL_RECEIVED = "email.received";
    public static final String EMAIL_TASKS_EXTRACTED = "email.tasks.extracted";
    public static final String TASK_CREATED = "task.created";
    public static final String TASK_UPDATED = "task.updated";
    public static final String DOCUMENT_UPLOADED = "document.uploaded";
    public static final String DOCUMENT_EMBEDDED = "document.embedded";
    public static final String REPORT_GENERATION_REQUESTED = "report.generation.requested";
    public static final String REPORT_GENERATED = "report.generated";
    public static final String NOTIFICATION_DISPATCH_REQUESTED = "notification.dispatch.requested";
    public static final String NOTIFICATION_DISPATCHED = "notification.dispatched";

    private PlatformEvents() {
    }
}
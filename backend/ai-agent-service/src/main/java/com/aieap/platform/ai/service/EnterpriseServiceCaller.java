package com.aieap.platform.ai.service;

import com.aieap.platform.ai.client.EmailClient;
import com.aieap.platform.ai.client.NotificationClient;
import com.aieap.platform.ai.client.ReportClient;
import com.aieap.platform.ai.client.TaskClient;
import com.aieap.platform.ai.client.WorkflowClient;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * EnterpriseServiceCaller orchestrates calls to all platform microservices.
 * This is the central integration point that enables AI Agent to fetch real enterprise data.
 */
@Service
public class EnterpriseServiceCaller {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseServiceCaller.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    @Autowired(required = false)
    private TaskClient taskClient;

    @Autowired(required = false)
    private EmailClient emailClient;

    @Autowired(required = false)
    private NotificationClient notificationClient;

    @Autowired(required = false)
    private WorkflowClient workflowClient;

    @Autowired(required = false)
    private ReportClient reportClient;

    /**
     * Fetch tasks with optional filters.
     */
    public String fetchTasks(String status, String priority, int limit) {
        if (taskClient == null) {
            log.warn("TaskClient not available");
            return "Task service is currently unavailable.";
        }

        try {
            var response = taskClient.list(0, limit, status, priority);
            if (response.data() == null || response.data().items() == null || response.data().items().isEmpty()) {
                return "No tasks found" + (status != null ? " with status: " + status : "");
            }

            var tasks = response.data().items();
            StringJoiner sj = new StringJoiner("\n");
            sj.add("Found " + tasks.size() + " task(s):");

            for (var task : tasks) {
                String due = task.dueAt() != null ? "Due: " + task.dueAt().format(DATE_FORMAT) : "No due date";
                sj.add("• [" + task.status() + "] " + task.title() + " (Priority: " + task.priority() + ", " + due + ")");
            }

            return sj.toString();
        } catch (Exception e) {
            log.error("Failed to fetch tasks: {}", e.getMessage());
            return "Error fetching tasks: " + e.getMessage();
        }
    }

    /**
     * Fetch task board view (kanban).
     */
    public String fetchTaskBoard() {
        if (taskClient == null) {
            return "Task service is currently unavailable.";
        }

        try {
            var response = taskClient.board();
            if (response.data() == null || response.data().columns() == null) {
                return "No task board data available";
            }

            StringJoiner sj = new StringJoiner(" | ");
            for (var column : response.data().columns()) {
                sj.add(column.status() + ":" + column.tasks().size());
            }
            return "Task Board: " + sj.toString();
        } catch (Exception e) {
            log.error("Failed to fetch task board: {}", e.getMessage());
            return "Error fetching task board: " + e.getMessage();
        }
    }

    /**
     * Fetch emails.
     */
    public String fetchEmails(int limit) {
        if (emailClient == null) {
            log.warn("EmailClient not available");
            return "Email service is currently unavailable.";
        }

        try {
            var response = emailClient.list(0, limit);
            if (response.data() == null || response.data().items() == null || response.data().items().isEmpty()) {
                return "No emails found";
            }

            var emails = response.data().items();
            StringJoiner sj = new StringJoiner("\n");
            sj.add("Found " + emails.size() + " email(s):");

            for (var email : emails) {
                String received = email.receivedAt() != null ? email.receivedAt().format(DATE_FORMAT) : "";
                sj.add("• From: " + email.sender() + " | Subject: " + truncate(email.subject(), 50) + " | " + received);
            }

            return sj.toString();
        } catch (Exception e) {
            log.error("Failed to fetch emails: {}", e.getMessage());
            return "Error fetching emails: " + e.getMessage();
        }
    }

    /**
     * Fetch email statistics.
     */
    public String fetchEmailStats() {
        if (emailClient == null) {
            return "Email service is currently unavailable.";
        }

        try {
            var response = emailClient.stats();
            if (response.data() == null) {
                return "No email statistics available";
            }
            return "Email Stats: " + response.data().toString();
        } catch (Exception e) {
            log.error("Failed to fetch email stats: {}", e.getMessage());
            return "Error fetching email stats: " + e.getMessage();
        }
    }

    /**
     * Fetch notifications.
     */
    public String fetchNotifications(int limit) {
        if (notificationClient == null) {
            log.warn("NotificationClient not available");
            return "Notification service is currently unavailable.";
        }

        try {
            var response = notificationClient.recent(limit);
            if (response.data() == null || response.data().isEmpty()) {
                return "No notifications found";
            }

            var notifications = response.data();
            StringJoiner sj = new StringJoiner("\n");
            sj.add("Found " + notifications.size() + " notification(s):");

            for (var notification : notifications) {
                String created = notification.createdAt() != null ? notification.createdAt().format(DATE_FORMAT) : "";
                sj.add("• [" + notification.status() + "] " + notification.title() + " - " + created);
            }

            return sj.toString();
        } catch (Exception e) {
            log.error("Failed to fetch notifications: {}", e.getMessage());
            return "Error fetching notifications: " + e.getMessage();
        }
    }

    /**
     * Fetch workflows.
     */
    public String fetchWorkflows() {
        if (workflowClient == null) {
            log.warn("WorkflowClient not available");
            return "Workflow service is currently unavailable.";
        }

        try {
            var response = workflowClient.list();
            if (response.data() == null || response.data().isEmpty()) {
                return "No workflows found";
            }

            var workflows = response.data();
            StringJoiner sj = new StringJoiner("\n");
            sj.add("Found " + workflows.size() + " workflow(s):");

            for (var workflow : workflows) {
                sj.add("• [" + workflow.status() + "] " + workflow.name() + " - " + (workflow.description() != null ? truncate(workflow.description(), 40) : ""));
            }

            return sj.toString();
        } catch (Exception e) {
            log.error("Failed to fetch workflows: {}", e.getMessage());
            return "Error fetching workflows: " + e.getMessage();
        }
    }

    /**
     * Fetch reports.
     */
    public String fetchReports() {
        if (reportClient == null) {
            log.warn("ReportClient not available");
            return "Report service is currently unavailable.";
        }

        try {
            var response = reportClient.list(0, 10, null);
            if (response.data() == null || response.data().items() == null || response.data().items().isEmpty()) {
                return "No reports found";
            }

            var reports = response.data().items();
            StringJoiner sj = new StringJoiner("\n");
            sj.add("Found " + reports.size() + " report(s):");

            for (var report : reports) {
                String created = report.createdAt() != null ? report.createdAt().format(DATE_FORMAT) : "";
                sj.add("• [" + report.status() + "] " + report.title() + " (" + report.type() + ") - " + created);
            }

            return sj.toString();
        } catch (Exception e) {
            log.error("Failed to fetch reports: {}", e.getMessage());
            return "Error fetching reports: " + e.getMessage();
        }
    }

    /**
     * Generate a quick report.
     */
    public String generateQuickReport(String title, String type) {
        if (reportClient == null) {
            return "Report service is currently unavailable.";
        }

        try {
            var request = new ReportClient.GenerateReportRequest(title, type != null ? type : "summary", "text");
            var response = reportClient.generate(request);

            if (response.data() == null) {
                return "Failed to generate report";
            }

            var report = response.data();
            return "Report generated: " + report.title() + " (Status: " + report.status() + ")";
        } catch (Exception e) {
            log.error("Failed to generate report: {}", e.getMessage());
            return "Error generating report: " + e.getMessage();
        }
    }

    /**
     * Get dashboard summary - combines key metrics from all services.
     */
    public String getDashboardSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Platform Dashboard ===\n");
        sb.append(fetchTaskBoard()).append("\n");
        sb.append(fetchEmails(5)).append("\n");
        sb.append(fetchNotifications(5));
        return sb.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }
}
